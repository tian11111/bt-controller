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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
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

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _connectionType = MutableStateFlow(ConnectionType.NONE)
    val connectionType: StateFlow<ConnectionType> = _connectionType.asStateFlow()

    private val _unifiedState = MutableStateFlow(UnifiedConnectionState.DISCONNECTED)
    val unifiedState: StateFlow<UnifiedConnectionState> = _unifiedState.asStateFlow()

    val receivedData: SharedFlow<String>
        get() = when (_connectionType.value) {
            ConnectionType.BLUETOOTH -> btManager.receivedData
            ConnectionType.WIFI -> wifiManager.receivedData
            ConnectionType.NONE -> btManager.receivedData // fallback
        }

    init {
        // 监听蓝牙状态变化
        scope.launch {
            btManager.connectionState.collect { btState ->
                if (_connectionType.value == ConnectionType.BLUETOOTH) {
                    _unifiedState.value = when (btState) {
                        BtConnectionState.DISCONNECTED -> UnifiedConnectionState.DISCONNECTED
                        BtConnectionState.CONNECTING -> UnifiedConnectionState.CONNECTING
                        BtConnectionState.CONNECTED -> UnifiedConnectionState.CONNECTED
                        BtConnectionState.ERROR -> UnifiedConnectionState.ERROR
                    }
                }
            }
        }

        // 监听 WiFi 状态变化
        scope.launch {
            wifiManager.connectionState.collect { wifiState ->
                if (_connectionType.value == ConnectionType.WIFI) {
                    _unifiedState.value = when (wifiState) {
                        WifiConnectionState.DISCONNECTED -> UnifiedConnectionState.DISCONNECTED
                        WifiConnectionState.CONNECTING -> UnifiedConnectionState.CONNECTING
                        WifiConnectionState.CONNECTED -> UnifiedConnectionState.CONNECTED
                        WifiConnectionState.ERROR -> UnifiedConnectionState.ERROR
                    }
                }
            }
        }
    }

    fun connectBluetooth(device: BluetoothDevice) {
        disconnectAll()
        _connectionType.value = ConnectionType.BLUETOOTH
        btManager.connect(device)
    }

    fun connectWifi(config: WifiConfig) {
        disconnectAll()
        _connectionType.value = ConnectionType.WIFI
        wifiManager.connect(config)
    }

    fun send(data: String): Boolean {
        return when (_connectionType.value) {
            ConnectionType.BLUETOOTH -> btManager.send(data)
            ConnectionType.WIFI -> wifiManager.send(data)
            ConnectionType.NONE -> false
        }
    }

    fun sendBytes(data: ByteArray): Boolean {
        return when (_connectionType.value) {
            ConnectionType.BLUETOOTH -> btManager.sendBytes(data)
            ConnectionType.WIFI -> wifiManager.sendBytes(data)
            ConnectionType.NONE -> false
        }
    }

    fun disconnect() {
        when (_connectionType.value) {
            ConnectionType.BLUETOOTH -> btManager.disconnect()
            ConnectionType.WIFI -> wifiManager.disconnect()
            ConnectionType.NONE -> {}
        }
        _connectionType.value = ConnectionType.NONE
        _unifiedState.value = UnifiedConnectionState.DISCONNECTED
    }

    private fun disconnectAll() {
        btManager.disconnect()
        wifiManager.disconnect()
    }

    fun destroy() {
        btManager.destroy()
        wifiManager.destroy()
        scope.cancel()
    }
}
