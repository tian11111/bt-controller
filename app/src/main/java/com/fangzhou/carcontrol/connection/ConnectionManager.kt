package com.fangzhou.carcontrol.connection

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.fangzhou.carcontrol.bluetooth.BluetoothManager
import com.fangzhou.carcontrol.bluetooth.ConnectionState as BtConnectionState
import com.fangzhou.carcontrol.wifi.WifiConfig
import com.fangzhou.carcontrol.wifi.WifiConnectionState
import com.fangzhou.carcontrol.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ConnectionType {
    NONE, BLUETOOTH, WIFI
}

enum class UnifiedConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

class ConnectionManager(private val context: Context) {

    val btManager = BluetoothManager(context)
    val wifiManager = WifiManager(context)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionType = MutableStateFlow(ConnectionType.NONE)
    val connectionType: StateFlow<ConnectionType> = _connectionType.asStateFlow()

    private val _unifiedState = MutableStateFlow(UnifiedConnectionState.DISCONNECTED)
    val unifiedState: StateFlow<UnifiedConnectionState> = _unifiedState.asStateFlow()

    private val _receivedData = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val receivedData: SharedFlow<String> = _receivedData.asSharedFlow()

    private val scheduler = OutboundScheduler(
        scope = scope,
        isConnected = { _unifiedState.value == UnifiedConnectionState.CONNECTED },
        writeFrame = { data ->
            when (_connectionType.value) {
                ConnectionType.BLUETOOTH -> btManager.writeFrame(data)
                ConnectionType.WIFI -> wifiManager.writeFrame(data)
                ConnectionType.NONE -> false
            }
        },
        minFrameIntervalMs = 40L
    )
    val sendEvents: SharedFlow<SendEvent> = scheduler.events

    init {
        // 只接收当前活动传输的数据，避免切换连接时旧连接的回调污染ACK和UI。
        scope.launch {
            btManager.receivedData.collect { raw ->
                if (_connectionType.value == ConnectionType.BLUETOOTH) {
                    scheduler.onIncomingData(raw)
                    _receivedData.emit(raw)
                }
            }
        }
        scope.launch {
            wifiManager.receivedData.collect { raw ->
                if (_connectionType.value == ConnectionType.WIFI) {
                    scheduler.onIncomingData(raw)
                    _receivedData.emit(raw)
                }
            }
        }

        scope.launch {
            btManager.connectionState.collect { state ->
                if (_connectionType.value == ConnectionType.BLUETOOTH) {
                    updateUnifiedState(
                        when (state) {
                            BtConnectionState.DISCONNECTED -> UnifiedConnectionState.DISCONNECTED
                            BtConnectionState.CONNECTING -> UnifiedConnectionState.CONNECTING
                            BtConnectionState.CONNECTED -> UnifiedConnectionState.CONNECTED
                            BtConnectionState.ERROR -> UnifiedConnectionState.ERROR
                        }
                    )
                }
            }
        }

        scope.launch {
            wifiManager.connectionState.collect { state ->
                if (_connectionType.value == ConnectionType.WIFI) {
                    updateUnifiedState(
                        when (state) {
                            WifiConnectionState.DISCONNECTED -> UnifiedConnectionState.DISCONNECTED
                            WifiConnectionState.CONNECTING -> UnifiedConnectionState.CONNECTING
                            WifiConnectionState.CONNECTED -> UnifiedConnectionState.CONNECTED
                            WifiConnectionState.ERROR -> UnifiedConnectionState.ERROR
                        }
                    )
                }
            }
        }
    }

    private fun updateUnifiedState(newState: UnifiedConnectionState) {
        val oldState = _unifiedState.value
        if (oldState == newState) return
        _unifiedState.value = newState
        if (newState == UnifiedConnectionState.CONNECTED) {
            scheduler.beginSession()
        } else if (oldState == UnifiedConnectionState.CONNECTED ||
            newState == UnifiedConnectionState.DISCONNECTED ||
            newState == UnifiedConnectionState.ERROR
        ) {
            scheduler.reset()
        }
    }

    fun connectBluetooth(device: BluetoothDevice) {
        disconnectAll()
        scheduler.reset()
        _connectionType.value = ConnectionType.BLUETOOTH
        _unifiedState.value = UnifiedConnectionState.CONNECTING
        btManager.connect(device)
    }

    fun connectWifi(config: WifiConfig) {
        disconnectAll()
        scheduler.reset()
        _connectionType.value = ConnectionType.WIFI
        _unifiedState.value = UnifiedConnectionState.CONNECTING
        wifiManager.connect(config)
    }

    fun sendRealtime(key: String, data: String): Boolean = scheduler.submitRealtime(key, data)

    fun sendStopStream(key: String, data: String, totalSends: Int = 4): Boolean =
        scheduler.submitStop(key, data, totalSends)

    fun sendEmergencyStop(
        joystickStop: String,
        gripperStop: String,
        totalSends: Int = 4
    ): Boolean = scheduler.submitEmergencyStop(joystickStop, gripperStop, totalSends)

    fun sendReliable(key: String, data: String, ackToken: String): Boolean =
        scheduler.submitReliable(key, data, ackToken, ackTimeoutMs = 150L, maxAttempts = 3)

    /** 查询、自定义命令等走有界FIFO，不再与实时运动帧互相覆盖。 */
    fun send(data: String): Boolean = scheduler.submitNormal(data)

    fun sendBytes(data: ByteArray): Boolean = scheduler.submitNormalBytes(data)

    fun disconnect() {
        scheduler.reset()
        when (_connectionType.value) {
            ConnectionType.BLUETOOTH -> btManager.disconnect()
            ConnectionType.WIFI -> wifiManager.disconnect()
            ConnectionType.NONE -> Unit
        }
        _connectionType.value = ConnectionType.NONE
        _unifiedState.value = UnifiedConnectionState.DISCONNECTED
    }

    private fun disconnectAll() {
        btManager.disconnect()
        wifiManager.disconnect()
    }

    fun destroy() {
        scheduler.reset()
        btManager.destroy()
        wifiManager.destroy()
        scope.cancel()
    }
}
