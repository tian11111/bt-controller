package com.fangzhou.carcontrol

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fangzhou.carcontrol.connection.ConnectionManager
import com.fangzhou.carcontrol.connection.ConnectionPreferences
import com.fangzhou.carcontrol.connection.ConnectionType
import com.fangzhou.carcontrol.bluetooth.ProtocolCommandStore
import com.fangzhou.carcontrol.bluetooth.ProtocolEngine
import com.fangzhou.carcontrol.bluetooth.ProtocolMessage
import com.fangzhou.carcontrol.layout.LayoutConfig
import com.fangzhou.carcontrol.layout.LayoutPreferences
import com.fangzhou.carcontrol.layout.WidgetLayout
import com.fangzhou.carcontrol.wifi.WifiConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CarState(
    val motorSpeeds: List<Int> = listOf(0, 0, 0, 0),
    val lastReceivedRaw: String = "",
    val logMessages: List<String> = emptyList(),
    val moveX: Float = 0f,      // -1 ~ 1
    val moveY: Float = 0f,      // -1 ~ 1
    val turnX: Float = 0f,      // -1 ~ 1
    val gripperUpDown: Float = 0f,  // -1 ~ 1
    val gripperOpen: Boolean = false,
    val gripperClose: Boolean = false,
    val valveOn: Boolean = false,  // 电磁阀1状态（夹爪开闭）
    val valve2On: Boolean = false   // 电磁阀2状态（夹爪伸缩）
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val connectionManager = ConnectionManager(application)
    val protocolEngine = ProtocolEngine()
    val protocolStore = ProtocolCommandStore(application)
    private val layoutPrefs = LayoutPreferences(application)
    private val connectionPrefs = ConnectionPreferences(application)

    private val _carState = MutableStateFlow(CarState())
    val carState: StateFlow<CarState> = _carState.asStateFlow()

    private val _layoutConfig = MutableStateFlow(layoutPrefs.load())
    val layoutConfig: StateFlow<LayoutConfig> = _layoutConfig.asStateFlow()

    private val logBuffer = ArrayDeque<String>(100)

    init {
        viewModelScope.launch {
            connectionManager.receivedData.collect { raw ->
                processReceivedData(raw)
            }
        }
    }

    fun getPairedDevices(): List<BluetoothDevice> {
        connectionManager.btManager.refreshPairedDevices()
        return connectionManager.btManager.pairedDevices.value
    }

    fun connectBluetooth(device: BluetoothDevice) {
        connectionManager.connectBluetooth(device)
        addLog("连接蓝牙: ${device.name ?: "unknown"}")
    }

    fun connectWifi(config: WifiConfig) {
        connectionManager.connectWifi(config)
        addLog("连接WiFi: ${config.ip}:${config.port} @ ${config.baudRate} baud")
    }

    fun loadLastWifiConfig(): WifiConfig = connectionPrefs.loadWifiConfig()
    fun saveLastWifiConfig(config: WifiConfig) = connectionPrefs.saveWifiConfig(config)

    fun disconnect() {
        connectionManager.disconnect()
        addLog("断开连接")
    }

    private fun processReceivedData(raw: String) {
        val messages = protocolEngine.parseRawData(raw)
        val state = _carState.value

        for (msg in messages) {
            when (msg) {
                is ProtocolMessage.MotorStatus -> {
                    _carState.value = state.copy(
                        motorSpeeds = msg.speeds,
                        lastReceivedRaw = raw.trim()
                    )
                }
                is ProtocolMessage.PlotData -> {
                    _carState.value = state.copy(
                        motorSpeeds = msg.data,
                        lastReceivedRaw = raw.trim()
                    )
                }
                is ProtocolMessage.Valve -> {
                    addLog("RX: ${msg.valveIndex}=${msg.state}")
                    if (msg.valveIndex == "valve2") {
                        _carState.value = state.copy(
                            valve2On = msg.isOn,
                            lastReceivedRaw = raw.trim()
                        )
                    } else {
                        _carState.value = state.copy(
                            valveOn = msg.isOn,
                            lastReceivedRaw = raw.trim()
                        )
                    }
                }
                is ProtocolMessage.ValveError -> {
                    addLog("RX: valve error ${msg.error}")
                    _carState.value = state.copy(lastReceivedRaw = raw.trim())
                }
                is ProtocolMessage.Raw -> {
                    addLog("RX: ${msg.command} ${msg.params}")
                    _carState.value = state.copy(lastReceivedRaw = raw.trim())
                }
                is ProtocolMessage.Text -> {
                    addLog("RX: ${msg.content}")
                }
                else -> {
                    _carState.value = state.copy(lastReceivedRaw = raw.trim())
                }
            }
        }

        if (messages.isEmpty() && raw.isNotBlank()) {
            addLog("RX: ${raw.trim()}")
            _carState.value = state.copy(lastReceivedRaw = raw.trim())
        }
    }

    fun sendJoystick(lx: Int, ly: Int, rx: Int = 0, ry: Int = 0) {
        val data = protocolEngine.createJoystick(lx, ly, rx, ry)
        connectionManager.send(data)
    }

    fun sendGripper(xSpeed: Int, ySpeed: Int) {
        val data = protocolEngine.createGripper(xSpeed, ySpeed)
        connectionManager.send(data)
    }

    // ===== 电磁阀（夹爪开合） =====
    fun sendValveOn() {
        connectionManager.send(protocolEngine.createValve1On())
    }

    fun sendValveOff() {
        connectionManager.send(protocolEngine.createValve1Off())
    }

    // ===== 电磁阀2（夹爪伸缩） =====
    fun sendValve2On() {
        connectionManager.send(protocolEngine.createValve2On())
    }

    fun sendValve2Off() {
        connectionManager.send(protocolEngine.createValve2Off())
    }

    fun sendValveToggle() {
        connectionManager.send(protocolEngine.createValveToggle())
    }

    fun sendValvePulse(ms: Int = 150) {
        connectionManager.send(protocolEngine.createValvePulse(ms))
    }

    fun sendValveQuery() {
        connectionManager.send(protocolEngine.createValveQuery())
    }

    fun setValveOn(on: Boolean) {
        _carState.value = _carState.value.copy(valveOn = on)
    }

    fun setValve2On(on: Boolean) {
        _carState.value = _carState.value.copy(valve2On = on)
    }

    fun sendQuery() {
        connectionManager.send(protocolEngine.createQuery())
    }

    fun sendAutoPlot(enable: Boolean) {
        if (enable) {
            connectionManager.send(protocolEngine.createAutoStart())
        } else {
            connectionManager.send(protocolEngine.createAutoStop())
        }
    }

    fun sendStop() {
        connectionManager.send(protocolEngine.createJoystick(0, 0, 0, 0))
        connectionManager.send(protocolEngine.createGripper(0, 0))
    }

    fun sendCustomCommand(command: String) {
        val normalized = if (command.endsWith("\r\n")) command else "$command\r\n"
        connectionManager.send(normalized)
        addLog("TX: $command")
    }

    fun updateMoveJoystick(x: Float, y: Float) {
        _carState.value = _carState.value.copy(moveX = x, moveY = y)
    }

    fun updateTurnJoystick(x: Float) {
        _carState.value = _carState.value.copy(turnX = x)
    }

    fun updateGripperUpDown(value: Float) {
        _carState.value = _carState.value.copy(gripperUpDown = value)
    }

    fun setGripperOpen(open: Boolean) {
        _carState.value = _carState.value.copy(gripperOpen = open)
    }

    fun setGripperClose(close: Boolean) {
        _carState.value = _carState.value.copy(gripperClose = close)
    }

    private fun addLog(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        logBuffer.addLast("[$timestamp] $msg")
        if (logBuffer.size > 100) logBuffer.removeFirst()
        _carState.value = _carState.value.copy(logMessages = logBuffer.toList())
    }

    fun clearLog() {
        logBuffer.clear()
        _carState.value = _carState.value.copy(logMessages = emptyList())
    }

    // ===== Layout =====

    fun toggleEditMode() {
        val current = _layoutConfig.value
        _layoutConfig.value = current.copy(isEditing = !current.isEditing)
    }

    fun updateWidgetPosition(id: String, offsetX: Float, offsetY: Float) {
        val current = _layoutConfig.value
        val updated = current.widgets.map {
            if (it.id == id) it.copy(offsetX = offsetX, offsetY = offsetY) else it
        }
        _layoutConfig.value = current.copy(widgets = updated)
    }

    fun updateWidgetScale(id: String, scale: Float) {
        val current = _layoutConfig.value
        val updated = current.widgets.map {
            if (it.id == id) it.copy(scale = scale.coerceIn(0.5f, 2.0f)) else it
        }
        _layoutConfig.value = current.copy(widgets = updated)
    }

    fun toggleWidgetVisibility(id: String) {
        val current = _layoutConfig.value
        val updated = current.widgets.map {
            if (it.id == id) it.copy(visible = !it.visible) else it
        }
        _layoutConfig.value = current.copy(widgets = updated)
    }

    fun saveLayout() {
        layoutPrefs.save(_layoutConfig.value)
    }

    fun resetLayout() {
        layoutPrefs.reset()
        _layoutConfig.value = layoutPrefs.load()
    }

    fun getWidget(id: String): WidgetLayout {
        return _layoutConfig.value.widgets.find { it.id == id }
            ?: WidgetLayout(id = id)
    }

    // ===== 自定义按钮 =====

    fun addCustomButton(label: String, command: String, colorHex: Long) {
        val current = _layoutConfig.value
        val id = "custom_${System.currentTimeMillis()}"
        val newWidget = WidgetLayout(
            id = id,
            offsetX = 0.3f,
            offsetY = 0.5f,
            isCustom = true,
            label = label,
            command = command,
            colorHex = colorHex
        )
        _layoutConfig.value = current.copy(widgets = current.widgets + newWidget)
    }

    fun removeWidget(id: String) {
        val current = _layoutConfig.value
        _layoutConfig.value = current.copy(widgets = current.widgets.filter { it.id != id })
    }

    override fun onCleared() {
        super.onCleared()
        connectionManager.destroy()
    }
}
