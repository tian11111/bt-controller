package com.fangzhou.carcontrol.wifi

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

    // TCP 发送队列：CONFLATED 只保留最新帧，不堆积
    // WiFi write 不阻塞(不像蓝牙SPP)，BUFFERED 会积压导致停止帧排在旧帧后面
    // CONFLATED 确保停止帧立即替换运动帧，一帧一帧地发，行为接近蓝牙
    private val sendChannel = Channel<ByteArray>(Channel.CONFLATED)
    private var sendJob: Job? = null

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
                    outputStream?.write(data)
                    outputStream?.flush()
                } catch (e: IOException) {
                    Log.e(TAG, "WiFi send queue error", e)
                    break
                }
            }
        }
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
