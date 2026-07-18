package com.fangzhou.carcontrol.wifi

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
                sock.connect(InetSocketAddress(config.ip, config.port), CONNECT_TIMEOUT_MS)
                sock.soTimeout = 0
                sock.tcpNoDelay = true

                socket = sock
                inputStream = sock.getInputStream()
                outputStream = sock.getOutputStream()

                _connectionState.value = WifiConnectionState.CONNECTED
                Log.i(TAG, "WiFi TCP connected, baudRate=${config.baudRate}")
                startReading()
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
                if (isActive) Log.e(TAG, "WiFi read error", e)
            } finally {
                if (isActive) {
                    _connectionState.value = WifiConnectionState.DISCONNECTED
                    cleanup()
                }
            }
        }
    }

    /** 由统一发送调度器调用；本层不再保存第二套发送队列。 */
    suspend fun writeFrame(data: ByteArray): Boolean = writeMutex.withLock {
        if (_connectionState.value != WifiConnectionState.CONNECTED) return@withLock false
        withContext(Dispatchers.IO) {
            try {
                val output = outputStream ?: return@withContext false
                output.write(data)
                output.flush()
                true
            } catch (e: IOException) {
                Log.e(TAG, "WiFi write error", e)
                false
            }
        }
    }

    // 兼容旧调用；正常业务应通过ConnectionManager的统一调度器发送。
    fun send(data: String): Boolean = sendBytes(data.toByteArray(Charsets.UTF_8))

    fun sendBytes(data: ByteArray): Boolean {
        if (_connectionState.value != WifiConnectionState.CONNECTED) return false
        scope.launch { writeFrame(data) }
        return true
    }

    fun disconnect() {
        readJob?.cancel()
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
