package com.fangzhou.carcontrol.connection

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel

sealed class SendEvent {
    data class Ack(val key: String, val attempts: Int, val latencyMs: Long) : SendEvent()
    data class Failed(val key: String, val reason: String) : SendEvent()
}

/**
 * 所有蓝牙/WiFi发送都经过这个调度器：
 * - 急停/停止优先；
 * - 电磁阀等可靠命令等待ACK并有限重试；
 * - 摇杆和步进电机只保留各自最新一帧；
 * - 底层始终只有一个writer，避免突发写入和旧帧积压。
 */
class OutboundScheduler(
    private val scope: CoroutineScope,
    private val isConnected: () -> Boolean,
    private val writeFrame: suspend (ByteArray) -> Boolean,
    private val minFrameIntervalMs: Long = 40L
) {
    companion object {
        private const val TAG = "OutboundScheduler"
        private const val NORMAL_QUEUE_LIMIT = 16
        private const val MAX_REALTIME_BURST = 3
        private const val ACK_BUFFER_LIMIT = 512
    }

    private enum class FrameKind { EMERGENCY, STOP_REPEAT, RELIABLE, REALTIME, NORMAL }

    private data class Frame(
        val id: Long,
        val session: Long,
        val kind: FrameKind,
        val key: String,
        val data: ByteArray,
        val motionEpoch: Long = 0L,
        val realtimeVersion: Long = 0L,
        val reliableVersion: Long = 0L,
        val ackToken: String? = null,
        val ackTimeoutMs: Long = 150L,
        val maxAttempts: Int = 1,
        val attempt: Int = 1,
        val stopSequenceId: Long = 0L,
        val stopRemaining: Int = 0,
        val createdAtMs: Long = SystemClock.elapsedRealtime()
    )

    private data class PendingReliable(
        val frame: Frame,
        val sentAtMs: Long
    )

    private val lock = Any()
    private val wakeSignal = Channel<Unit>(Channel.CONFLATED)
    private val urgentQueue = ArrayDeque<Frame>()
    private val stopRepeatQueue = ArrayDeque<Frame>()
    private val reliableByKey = LinkedHashMap<String, Frame>()
    private val realtimeByKey = LinkedHashMap<String, Frame>()
    private val normalQueue = ArrayDeque<Frame>()
    private val realtimeVersions = HashMap<String, Long>()
    private val reliableVersions = HashMap<String, Long>()
    private val stopSequenceIds = HashMap<String, Long>()
    private val ackBuffer = StringBuilder()

    private var currentSession = 0L
    private var nextId = 1L
    private var motionEpoch = 0L
    private var pendingReliable: PendingReliable? = null
    private var lastWriteAtMs = 0L
    private var realtimeBurst = 0

    private val _events = MutableSharedFlow<SendEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<SendEvent> = _events.asSharedFlow()

    init {
        scope.launch { writerLoop() }
    }

    fun beginSession() {
        synchronized(lock) {
            currentSession++
            clearLocked()
            lastWriteAtMs = 0L
        }
        wakeSignal.trySend(Unit)
    }

    fun reset() {
        synchronized(lock) {
            currentSession++
            clearLocked()
            lastWriteAtMs = 0L
        }
    }

    fun submitRealtime(key: String, data: String): Boolean {
        if (!isConnected()) return false
        synchronized(lock) {
            cancelStopLocked(key)
            val version = (realtimeVersions[key] ?: 0L) + 1L
            realtimeVersions[key] = version
            val frame = Frame(
                id = nextId++,
                session = currentSession,
                kind = FrameKind.REALTIME,
                key = key,
                data = data.toByteArray(Charsets.UTF_8),
                motionEpoch = motionEpoch,
                realtimeVersion = version
            )
            realtimeByKey[key] = frame
        }
        wakeSignal.trySend(Unit)
        return true
    }

    /** totalSends包含第一次停止帧，默认总共发送4次。 */
    fun submitStop(key: String, data: String, totalSends: Int = 4): Boolean {
        if (!isConnected()) return false
        synchronized(lock) {
            realtimeByKey.remove(key)
            realtimeVersions[key] = (realtimeVersions[key] ?: 0L) + 1L
            val sequenceId = (stopSequenceIds[key] ?: 0L) + 1L
            stopSequenceIds[key] = sequenceId
            urgentQueue.addLast(
                Frame(
                    id = nextId++,
                    session = currentSession,
                    kind = FrameKind.EMERGENCY,
                    key = key,
                    data = data.toByteArray(Charsets.UTF_8),
                    motionEpoch = motionEpoch,
                    stopSequenceId = sequenceId,
                    stopRemaining = totalSends.coerceAtLeast(1)
                )
            )
        }
        wakeSignal.trySend(Unit)
        return true
    }

    fun submitEmergencyStop(joystickStop: String, gripperStop: String, totalSends: Int = 4): Boolean {
        if (!isConnected()) return false
        synchronized(lock) {
            motionEpoch++
            realtimeByKey.clear()
            urgentQueue.clear()
            stopRepeatQueue.clear()
            enqueueInitialStopLocked("joystick", joystickStop, totalSends)
            enqueueInitialStopLocked("gripper", gripperStop, totalSends)
        }
        wakeSignal.trySend(Unit)
        return true
    }

    fun submitReliable(
        key: String,
        data: String,
        ackToken: String,
        ackTimeoutMs: Long = 150L,
        maxAttempts: Int = 3
    ): Boolean {
        if (!isConnected()) return false
        synchronized(lock) {
            val version = (reliableVersions[key] ?: 0L) + 1L
            reliableVersions[key] = version
            if (pendingReliable?.frame?.key == key) {
                pendingReliable = null
            }
            reliableByKey[key] = Frame(
                id = nextId++,
                session = currentSession,
                kind = FrameKind.RELIABLE,
                key = key,
                data = data.toByteArray(Charsets.UTF_8),
                reliableVersion = version,
                ackToken = ackToken,
                ackTimeoutMs = ackTimeoutMs,
                maxAttempts = maxAttempts.coerceAtLeast(1)
            )
        }
        wakeSignal.trySend(Unit)
        return true
    }

    fun submitNormal(data: String, key: String = "normal"): Boolean {
        if (!isConnected()) return false
        synchronized(lock) {
            if (normalQueue.size >= NORMAL_QUEUE_LIMIT) {
                _events.tryEmit(SendEvent.Failed(key, "普通发送队列已满"))
                return false
            }
            normalQueue.addLast(
                Frame(
                    id = nextId++,
                    session = currentSession,
                    kind = FrameKind.NORMAL,
                    key = key,
                    data = data.toByteArray(Charsets.UTF_8)
                )
            )
        }
        wakeSignal.trySend(Unit)
        return true
    }

    fun submitNormalBytes(data: ByteArray, key: String = "normal"): Boolean {
        if (!isConnected()) return false
        synchronized(lock) {
            if (normalQueue.size >= NORMAL_QUEUE_LIMIT) return false
            normalQueue.addLast(
                Frame(
                    id = nextId++,
                    session = currentSession,
                    kind = FrameKind.NORMAL,
                    key = key,
                    data = data.copyOf()
                )
            )
        }
        wakeSignal.trySend(Unit)
        return true
    }

    fun onIncomingData(raw: String) {
        var ackEvent: SendEvent.Ack? = null
        synchronized(lock) {
            ackBuffer.append(raw)
            if (ackBuffer.length > ACK_BUFFER_LIMIT) {
                ackBuffer.delete(0, ackBuffer.length - ACK_BUFFER_LIMIT)
            }
            val pending = pendingReliable
            val token = pending?.frame?.ackToken
            if (pending != null && token != null) {
                val tokenIndex = ackBuffer.indexOf(token)
                if (tokenIndex >= 0) {
                    pendingReliable = null
                    ackBuffer.delete(0, tokenIndex + token.length)
                    ackEvent = SendEvent.Ack(
                        key = pending.frame.key,
                        attempts = pending.frame.attempt,
                        latencyMs = SystemClock.elapsedRealtime() - pending.sentAtMs
                    )
                }
            }
        }
        ackEvent?.let { _events.tryEmit(it) }
        if (ackEvent != null) wakeSignal.trySend(Unit)
    }

    private suspend fun writerLoop() {
        for (ignored in wakeSignal) {
            while (scope.isActive) {
                val frame = takeNextFrame() ?: break
                if (!isFrameStillValid(frame)) continue

                val now = SystemClock.elapsedRealtime()
                val waitMs = minFrameIntervalMs - (now - lastWriteAtMs)
                if (waitMs > 0L) delay(waitMs)

                if (!isFrameStillValid(frame) || !isConnected()) continue

                if (frame.kind == FrameKind.RELIABLE) {
                    synchronized(lock) {
                        frame.ackToken?.let { discardAckTokenLocked(it) }
                    }
                }

                val success = try {
                    writeFrame(frame.data)
                } catch (t: Throwable) {
                    Log.e(TAG, "write failed: ${frame.key}", t)
                    false
                }
                lastWriteAtMs = SystemClock.elapsedRealtime()

                if (!success) {
                    _events.tryEmit(SendEvent.Failed(frame.key, "底层写入失败"))
                }
                afterWrite(frame)
            }
        }
    }

    private fun takeNextFrame(): Frame? = synchronized(lock) {
        pollValidLocked(urgentQueue)?.let { return@synchronized it }

        // 停止补发也可抢占ACK等待，确保急停不是只发最前面的两帧。
        pollValidLocked(stopRepeatQueue)?.let {
            realtimeBurst = 0
            return@synchronized it
        }

        // 可靠命令等待ACK期间暂停普通/实时流量，急停和停止补发不受影响。
        if (pendingReliable != null) return@synchronized null

        if (reliableByKey.isNotEmpty()) {
            val first = reliableByKey.entries.first()
            reliableByKey.remove(first.key)
            realtimeBurst = 0
            return@synchronized first.value
        }

        if (normalQueue.isNotEmpty() && realtimeBurst >= MAX_REALTIME_BURST) {
            realtimeBurst = 0
            return@synchronized normalQueue.removeFirst()
        }

        if (realtimeByKey.isNotEmpty()) {
            val first = realtimeByKey.entries.first()
            realtimeByKey.remove(first.key)
            realtimeBurst++
            return@synchronized first.value
        }

        if (normalQueue.isNotEmpty()) {
            realtimeBurst = 0
            return@synchronized normalQueue.removeFirst()
        }

        null
    }

    private fun pollValidLocked(queue: ArrayDeque<Frame>): Frame? {
        while (queue.isNotEmpty()) {
            val frame = queue.removeFirst()
            if (isFrameStillValidLocked(frame)) return frame
        }
        return null
    }

    private fun isFrameStillValid(frame: Frame): Boolean = synchronized(lock) {
        isFrameStillValidLocked(frame)
    }

    private fun isFrameStillValidLocked(frame: Frame): Boolean {
        if (frame.session != currentSession) return false
        if (frame.kind == FrameKind.REALTIME &&
            (frame.motionEpoch != motionEpoch || realtimeVersions[frame.key] != frame.realtimeVersion)
        ) return false
        if ((frame.kind == FrameKind.EMERGENCY || frame.kind == FrameKind.STOP_REPEAT) &&
            stopSequenceIds[frame.key] != frame.stopSequenceId
        ) return false
        if (frame.kind == FrameKind.RELIABLE && reliableVersions[frame.key] != frame.reliableVersion) return false
        return true
    }

    private fun afterWrite(frame: Frame) {
        when (frame.kind) {
            FrameKind.RELIABLE -> startReliableWait(frame)
            FrameKind.EMERGENCY, FrameKind.STOP_REPEAT -> scheduleStopRepeat(frame)
            else -> Unit
        }
    }

    private fun startReliableWait(frame: Frame) {
        val token = frame.ackToken ?: return
        var immediateAck: SendEvent.Ack? = null
        synchronized(lock) {
            if (!isFrameStillValidLocked(frame)) return
            val tokenIndex = ackBuffer.indexOf(token)
            if (tokenIndex >= 0) {
                ackBuffer.delete(0, tokenIndex + token.length)
                immediateAck = SendEvent.Ack(frame.key, frame.attempt, 0L)
            } else {
                pendingReliable = PendingReliable(frame, SystemClock.elapsedRealtime())
            }
        }
        if (immediateAck != null) {
            _events.tryEmit(immediateAck!!)
            wakeSignal.trySend(Unit)
            return
        }

        scope.launch {
            delay(frame.ackTimeoutMs)
            var failedEvent: SendEvent.Failed? = null
            synchronized(lock) {
                val pending = pendingReliable
                if (pending?.frame?.id != frame.id) return@synchronized
                pendingReliable = null

                val stillLatest = reliableVersions[frame.key] == frame.reliableVersion
                if (stillLatest && frame.attempt < frame.maxAttempts && frame.session == currentSession) {
                    reliableByKey[frame.key] = frame.copy(
                        id = nextId++,
                        attempt = frame.attempt + 1,
                        createdAtMs = SystemClock.elapsedRealtime()
                    )
                } else if (stillLatest) {
                    failedEvent = SendEvent.Failed(frame.key, "等待ACK超时，已尝试${frame.attempt}次")
                }
            }
            failedEvent?.let { _events.tryEmit(it) }
            wakeSignal.trySend(Unit)
        }
    }

    private fun scheduleStopRepeat(frame: Frame) {
        if (frame.stopRemaining <= 1) return
        scope.launch {
            delay(minFrameIntervalMs)
            synchronized(lock) {
                if (!isFrameStillValidLocked(frame)) return@synchronized
                stopRepeatQueue.addLast(
                    frame.copy(
                        id = nextId++,
                        kind = FrameKind.STOP_REPEAT,
                        stopRemaining = frame.stopRemaining - 1,
                        createdAtMs = SystemClock.elapsedRealtime()
                    )
                )
            }
            wakeSignal.trySend(Unit)
        }
    }

    private fun enqueueInitialStopLocked(key: String, data: String, totalSends: Int) {
        val sequenceId = (stopSequenceIds[key] ?: 0L) + 1L
        stopSequenceIds[key] = sequenceId
        urgentQueue.addLast(
            Frame(
                id = nextId++,
                session = currentSession,
                kind = FrameKind.EMERGENCY,
                key = key,
                data = data.toByteArray(Charsets.UTF_8),
                motionEpoch = motionEpoch,
                stopSequenceId = sequenceId,
                stopRemaining = totalSends.coerceAtLeast(1)
            )
        )
    }

    private fun cancelStopLocked(key: String) {
        stopSequenceIds[key] = (stopSequenceIds[key] ?: 0L) + 1L
    }

    private fun discardAckTokenLocked(token: String) {
        var index = ackBuffer.indexOf(token)
        while (index >= 0) {
            ackBuffer.delete(index, index + token.length)
            index = ackBuffer.indexOf(token)
        }
    }

    private fun clearLocked() {
        urgentQueue.clear()
        stopRepeatQueue.clear()
        reliableByKey.clear()
        realtimeByKey.clear()
        normalQueue.clear()
        pendingReliable = null
        realtimeVersions.clear()
        reliableVersions.clear()
        stopSequenceIds.clear()
        ackBuffer.setLength(0)
        motionEpoch++
        realtimeBurst = 0
    }
}
