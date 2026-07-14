package com.fangzhou.carcontrol.wifi

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

enum class WifiConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

data class WifiConfig(
    val ip: String = "192.168.4.1",
    val port: Int = 9000,
    val baudRate: Int = 9600
)

class WifiManager(private val context: Context) {

    companion object {
        private const val TAG = "WifiManager"
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_BUFFER_SIZE = 1024
    }

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // TCP 发送队列：与蓝牙 classicSendChannel 相同策略
    // BUFFERED + DROP_OLDEST：保留缓冲，满时丢最旧帧，停止帧可多次入队保证送达
    private val sendChannel = Channel<ByteArray>(
        Channel.BUFFERED,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private var sendJob: Job? = null

    // 保护所有 socket 写操作，防止紧急写与队列写交错
    private val writeMutex = Mutex()

    private val _connectionState = MutableStateFlow(WifiConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WifiConnectionState> = _connectionState.asStateFlow()

    private val _receivedData = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val receivedData: SharedFlow<String> = _receivedData.asSharedFlow()

    fun connect(config: WifiConfig) {
        if (_connectionState.value == WifiConnectionState.CONNECTING) {
            Log.w(TAG, "Already connecting")
            return
        }
        
        disconnect()
        _connectionState.value = WifiConnectionState.CONNECTING
        Log.i(TAG, "Connecting to TCP ${config.ip}:${config.port}, baudRate=${config.baudRate}")

        scope.launch {
            try {
                val sock = Socket()
                sock.connect(
                    InetSocketAddress(config.ip, config.port),
                    CONNECT_TIMEOUT_MS
                )
                sock.soTimeout = 0 // 无限等待读取
                sock.tcpNoDelay = true // 禁用 Nagle 算法，降低延迟

                socket = sock
                inputStream = sock.getInputStream()
                outputStream = sock.getOutputStream()

                _connectionState.value = WifiConnectionState.CONNECTED
                Log.i(TAG, "WiFi TCP connected, baudRate=${config.baudRate}")

                startReading()
                startSending()

            } catch (e: IOException) {
                Log.e(TAG, "WiFi connect failed", e)
                _connectionState.value = WifiConnectionState.ERROR
                cleanup()
            }
        }
    }

    private fun startReading() {
        readJob?.cancel()
        readJob = scope.launch {
            val buffer = ByteArray(READ_BUFFER_SIZE)
            try {
                val input = inputStream ?: return@launch
                while (isActive) {
                    val count = input.read(buffer)
                    if (count > 0) {
                        val data = String(buffer, 0, count, Charsets.UTF_8)
                        Log.d(TAG, "WiFi RX: $data")
                        _receivedData.emit(data)
                    } else if (count < 0) {
                        Log.w(TAG, "WiFi stream closed")
                        break
                    }
                }
            } catch (e: IOException) {
                if (isActive) {
                    Log.e(TAG, "WiFi read error", e)
                }
            } finally {
                if (isActive) {
                    _connectionState.value = WifiConnectionState.DISCONNECTED
                    cleanup()
                }
            }
        }
    }

    fun send(data: String): Boolean = sendBytes(data.toByteArray(Charsets.UTF_8))

    fun sendBytes(data: ByteArray): Boolean {
        return sendChannel.trySend(data).isSuccess
    }

    private fun startSending() {
        sendJob?.cancel()
        sendJob = scope.launch {
            for (data in sendChannel) {
                try {
                    writeMutex.withLock {
                        outputStream?.write(data)
                        outputStream?.flush()
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "WiFi send queue error", e)
                    break
                }
            }
        }
    }

    /**
     * 紧急发送：清空队列后直接写 socket，绕过排队延迟。
     * 用于停止帧/急停，确保以最快速度送达。
     */
    fun sendUrgent(data: String): Boolean = sendUrgentBytes(data.toByteArray(Charsets.UTF_8))

    fun sendUrgentBytes(data: ByteArray): Boolean {
        while (sendChannel.tryReceive().isSuccess) { /* drain */ }
        scope.launch(Dispatchers.IO) {
            try {
                writeMutex.withLock {
                    outputStream?.write(data)
                    outputStream?.flush()
                }
            } catch (e: IOException) {
                Log.e(TAG, "WiFi urgent send error", e)
            }
        }
        return true
    }

    fun disconnect() {
        readJob?.cancel()
        sendJob?.cancel()
        while (sendChannel.tryReceive().isSuccess) { /* 清空待发送队列 */ }
        cleanup()
        _connectionState.value = WifiConnectionState.DISCONNECTED
    }

    private fun cleanup() {
        try { inputStream?.close() } catch (_: IOException) {}
        try { outputStream?.close() } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        inputStream = null
        outputStream = null
        socket = null
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }
}
