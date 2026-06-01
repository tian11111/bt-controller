package com.fangzhou.carcontrol

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fangzhou.carcontrol.bluetooth.BluetoothManager
import com.fangzhou.carcontrol.bluetooth.ConnectionState
import com.fangzhou.carcontrol.bluetooth.ProtocolCommandStore
import com.fangzhou.carcontrol.bluetooth.ProtocolEngine
import com.fangzhou.carcontrol.bluetooth.ProtocolMessage
import com.fangzhou.carcontrol.layout.LayoutConfig
import com.fangzhou.carcontrol.layout.LayoutPreferences
import com.fangzhou.carcontrol.layout.WidgetLayout
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
    val gripperClose: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val btManager = BluetoothManager(application)
    val protocolEngine = ProtocolEngine()
    val protocolStore = ProtocolCommandStore(application)
    private val layoutPrefs = LayoutPreferences(application)

    private val _carState = MutableStateFlow(CarState())
    val carState: StateFlow<CarState> = _carState.asStateFlow()

    private val _layoutConfig = MutableStateFlow(layoutPrefs.load())
    val layoutConfig: StateFlow<LayoutConfig> = _layoutConfig.asStateFlow()

    private val logBuffer = ArrayDeque<String>(100)

    init {
        viewModelScope.launch {
            btManager.receivedData.collect { raw ->
                processReceivedData(raw)
            }
        }
    }

    fun getPairedDevices(): List<BluetoothDevice> {
        btManager.refreshPairedDevices()
        return btManager.pairedDevices.value
    }

    fun connectDevice(device: BluetoothDevice) {
        btManager.connect(device)
    }

    fun disconnect() {
        btManager.disconnect()
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
        btManager.send(data)
    }

    fun sendGripper(xSpeed: Int, ySpeed: Int) {
        val data = protocolEngine.createGripper(xSpeed, ySpeed)
        btManager.send(data)
    }

    fun sendQuery() {
        btManager.send(protocolEngine.createQuery())
    }

    fun sendAutoPlot(enable: Boolean) {
        if (enable) {
            btManager.send(protocolEngine.createAutoStart())
        } else {
            btManager.send(protocolEngine.createAutoStop())
        }
    }

    fun sendStop() {
        btManager.send(protocolEngine.createJoystick(0, 0, 0, 0))
        btManager.send(protocolEngine.createGripper(0, 0))
    }

    fun sendCustomCommand(command: String) {
        btManager.send("$command\r\n")
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

    override fun onCleared() {
        super.onCleared()
        btManager.destroy()
    }
}
