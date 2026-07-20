package com.fangzhou.carcontrol

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fangzhou.carcontrol.bluetooth.ProtocolCommandStore
import com.fangzhou.carcontrol.bluetooth.ProtocolEngine
import com.fangzhou.carcontrol.bluetooth.ProtocolMessage
import com.fangzhou.carcontrol.connection.ConnectionManager
import com.fangzhou.carcontrol.connection.ConnectionPreferences
import com.fangzhou.carcontrol.connection.SendEvent
import com.fangzhou.carcontrol.connection.UnifiedConnectionState
import com.fangzhou.carcontrol.layout.LayoutConfig
import com.fangzhou.carcontrol.layout.LayoutPreferences
import com.fangzhou.carcontrol.layout.WidgetLayout
import com.fangzhou.carcontrol.wifi.WifiConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

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

    companion object {
        private const val CONTROL_POLL_MS = 10L
        private const val CONTROL_MIN_SEND_MS = 20L
        private const val CONTROL_HEARTBEAT_MS = 100L
        private const val CONTROL_CHANGE_THRESHOLD = 10
    }

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
        viewModelScope.launch {
            connectionManager.sendEvents.collect { event ->
                when (event) {
                    is SendEvent.Ack -> addLog(
                        "TX确认: ${event.key}，第${event.attempts}次成功，${event.latencyMs}ms"
                    )
                    is SendEvent.Failed -> addLog("发送失败: ${event.key}，${event.reason}")
                }
            }
        }
        startControlLoop()
    }

    private fun startControlLoop() {
        viewModelScope.launch {
            var wasConnected = false
            var lastSentLx = 0
            var lastSentLy = 0
            var lastSentRx = 0
            var lastSentGx = 0
            var lastJoystickSendAt = 0L
            var lastGripperSendAt = 0L

            while (isActive) {
                val connected = connectionManager.unifiedState.value == UnifiedConnectionState.CONNECTED
                if (!connected) {
                    wasConnected = false
                    lastSentLx = 0
                    lastSentLy = 0
                    lastSentRx = 0
                    lastSentGx = 0
                    lastJoystickSendAt = 0L
                    lastGripperSendAt = 0L
                    delay(CONTROL_POLL_MS)
                    continue
                }

                if (!wasConnected) {
                    wasConnected = true
                    // 新连接先清一次残留运动状态；用户立即操作时，实时帧会取消对应停止重发。
                    connectionManager.sendEmergencyStop(
                        protocolEngine.createJoystick(0, 0, 0, 0),
                        protocolEngine.createGripper(0, 0),
                        totalSends = 2
                    )
                }

                val now = SystemClock.elapsedRealtime()
                val state = _carState.value
                val lx = normalizeControl(state.moveX, scale = 100, deadZone = 5, limit = 100)
                val ly = normalizeControl(state.moveY, scale = 100, deadZone = 5, limit = 100)
                val rx = normalizeControl(state.turnX, scale = 100, deadZone = 5, limit = 100)
                val gx = normalizeControl(state.gripperUpDown, scale = 300, deadZone = 20, limit = 300)

                val joystickIsZero = lx == 0 && ly == 0 && rx == 0
                val joystickWasZero = lastSentLx == 0 && lastSentLy == 0 && lastSentRx == 0
                if (joystickIsZero) {
                    if (!joystickWasZero) {
                        connectionManager.sendStopStream(
                            key = "joystick",
                            data = protocolEngine.createJoystick(0, 0, 0, 0),
                            totalSends = 4
                        )
                        lastSentLx = 0
                        lastSentLy = 0
                        lastSentRx = 0
                        lastJoystickSendAt = now
                    }
                } else if (now - lastJoystickSendAt >= CONTROL_MIN_SEND_MS) {
                    val changed = maxOf(
                        abs(lx - lastSentLx),
                        abs(ly - lastSentLy),
                        abs(rx - lastSentRx)
                    ) >= CONTROL_CHANGE_THRESHOLD
                    val heartbeatDue = now - lastJoystickSendAt >= CONTROL_HEARTBEAT_MS
                    if (changed || heartbeatDue) {
                        sendJoystick(lx, ly, rx, 0)
                        lastSentLx = lx
                        lastSentLy = ly
                        lastSentRx = rx
                        lastJoystickSendAt = now
                    }
                }

                val gripperIsZero = gx == 0
                val gripperWasZero = lastSentGx == 0
                if (gripperIsZero) {
                    if (!gripperWasZero) {
                        connectionManager.sendStopStream(
                            key = "gripper",
                            data = protocolEngine.createGripper(0, 0),
                            totalSends = 4
                        )
                        lastSentGx = 0
                        lastGripperSendAt = now
                    }
                } else if (now - lastGripperSendAt >= CONTROL_MIN_SEND_MS) {
                    val changed = abs(gx - lastSentGx) >= CONTROL_CHANGE_THRESHOLD
                    val heartbeatDue = now - lastGripperSendAt >= CONTROL_HEARTBEAT_MS
                    if (changed || heartbeatDue) {
                        sendGripper(gx, gx)
                        lastSentGx = gx
                        lastGripperSendAt = now
                    }
                }

                delay(CONTROL_POLL_MS)
            }
        }
    }

    private fun normalizeControl(value: Float, scale: Int, deadZone: Int, limit: Int): Int {
        val scaled = (value * scale).toInt().coerceIn(-limit, limit)
        return if (abs(scaled) < deadZone) 0 else scaled
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

        for (msg in messages) {
            when (msg) {
                is ProtocolMessage.MotorStatus -> {
                    _carState.value = _carState.value.copy(
                        motorSpeeds = msg.speeds,
                        lastReceivedRaw = raw.trim()
                    )
                }
                is ProtocolMessage.PlotData -> {
                    _carState.value = _carState.value.copy(
                        motorSpeeds = msg.data,
                        lastReceivedRaw = raw.trim()
                    )
                }
                is ProtocolMessage.Valve -> {
                    addLog("RX: ${msg.valveIndex}=${msg.state}")
                    if (msg.valveIndex == "valve2") {
                        _carState.value = _carState.value.copy(
                            valve2On = msg.isOn,
                            lastReceivedRaw = raw.trim()
                        )
                    } else {
                        _carState.value = _carState.value.copy(
                            valveOn = msg.isOn,
                            lastReceivedRaw = raw.trim()
                        )
                    }
                }
                is ProtocolMessage.ValveError -> {
                    addLog("RX: valve error ${msg.error}")
                    _carState.value = _carState.value.copy(lastReceivedRaw = raw.trim())
                }
                is ProtocolMessage.Raw -> {
                    addLog("RX: ${msg.command} ${msg.params}")
                    _carState.value = _carState.value.copy(lastReceivedRaw = raw.trim())
                }
                is ProtocolMessage.Text -> {
                    addLog("RX: ${msg.content}")
                    _carState.value = _carState.value.copy(lastReceivedRaw = raw.trim())
                }
                else -> {
                    _carState.value = _carState.value.copy(lastReceivedRaw = raw.trim())
                }
            }
        }

        if (messages.isEmpty() && raw.isNotBlank()) {
            addLog("RX: ${raw.trim()}")
            _carState.value = _carState.value.copy(lastReceivedRaw = raw.trim())
        }
    }

    fun sendJoystick(lx: Int, ly: Int, rx: Int = 0, ry: Int = 0) {
        connectionManager.sendRealtime(
            key = "joystick",
            data = protocolEngine.createJoystick(lx, ly, rx, ry)
        )
    }

    fun sendGripper(xSpeed: Int, ySpeed: Int) {
        connectionManager.sendRealtime(
            key = "gripper",
            data = protocolEngine.createGripper(xSpeed, ySpeed)
        )
    }

    // ===== 电磁阀1（PE10，夹爪开合） =====
    fun sendValveOn() {
        connectionManager.sendReliable(
            key = "valve1",
            data = protocolEngine.createValve1On(),
            ackToken = "[valve1:on]"
        )
    }

    fun sendValveOff() {
        connectionManager.sendReliable(
            key = "valve1",
            data = protocolEngine.createValve1Off(),
            ackToken = "[valve1:off]"
        )
    }

    // ===== 电磁阀2（PE6，夹爪伸缩） =====
    fun sendValve2On() {
        connectionManager.sendReliable(
            key = "valve2",
            data = protocolEngine.createValve2On(),
            ackToken = "[valve2:on]"
        )
    }

    fun sendValve2Off() {
        connectionManager.sendReliable(
            key = "valve2",
            data = protocolEngine.createValve2Off(),
            ackToken = "[valve2:off]"
        )
    }

    fun sendValveToggle() {
        connectionManager.send(protocolEngine.createValve1Toggle())
    }

    fun sendValvePulse(ms: Int = 150) {
        connectionManager.sendReliable(
            key = "valve1",
            data = protocolEngine.createValve1Pulse(ms),
            ackToken = "[valve1:pulse]"
        )
    }

    fun sendValveQuery() {
        connectionManager.send(protocolEngine.createValve1Query())
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
        _carState.value = _carState.value.copy(
            moveX = 0f,
            moveY = 0f,
            turnX = 0f,
            gripperUpDown = 0f
        )
        connectionManager.sendEmergencyStop(
            joystickStop = protocolEngine.createJoystick(0, 0, 0, 0),
            gripperStop = protocolEngine.createGripper(0, 0),
            totalSends = 4
        )
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
