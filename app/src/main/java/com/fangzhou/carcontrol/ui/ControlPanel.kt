package com.fangzhou.carcontrol.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fangzhou.carcontrol.MainViewModel
import com.fangzhou.carcontrol.connection.ConnectionType
import com.fangzhou.carcontrol.connection.UnifiedConnectionState
import com.fangzhou.carcontrol.layout.WidgetIds
import kotlinx.coroutines.delay

@Composable
fun ControlPanel(
    viewModel: MainViewModel,
    connectionState: UnifiedConnectionState,
    connectionType: ConnectionType,
    onShowBtDialog: () -> Unit,
    onShowWifiDialog: () -> Unit,
    onShowProtocolEditor: () -> Unit
) {
    val carState by viewModel.carState.collectAsState()
    val layoutConfig by viewModel.layoutConfig.collectAsState()
    val isEditing = layoutConfig.isEditing

    var parentSize by remember { mutableStateOf(IntSize(1080, 600)) }
    var showAddButton by remember { mutableStateOf(false) }
    var showCustomCmd by remember { mutableStateOf(false) }

    // 自动发送
    LaunchedEffect(connectionState) {
        if (connectionState == UnifiedConnectionState.CONNECTED) {
            var lastCmd = ""
            var lastGx = 0
            var wasCenter = true
            while (true) {
                val s = viewModel.carState.value
                val lx = (s.moveX * 100).toInt().let { if (kotlin.math.abs(it) < 2) 0 else it }.coerceIn(-100, 100)
                val ly = (s.moveY * 100).toInt().let { if (kotlin.math.abs(it) < 2) 0 else it }.coerceIn(-100, 100)
                val rx = (s.turnX * 100).toInt().let { if (kotlin.math.abs(it) < 2) 0 else it }.coerceIn(-100, 100)
                val gx = (s.gripperUpDown * 300).toInt().let { if (kotlin.math.abs(it) < 10) 0 else it }.coerceIn(-300, 300)

                val cmd = "$lx,$ly,$rx"
                val isCenter = (lx == 0 && ly == 0 && rx == 0)

                if (cmd != lastCmd) {
                    viewModel.sendJoystick(lx, ly, rx, 0)
                    lastCmd = cmd
                }

                // 松手瞬间连发3次停机
                if (isCenter && !wasCenter) {
                    repeat(3) { viewModel.sendJoystick(0, 0, 0, 0); delay(20) }
                }
                wasCenter = isCenter

                if (gx != lastGx) {
                    viewModel.sendGripper(0, gx)
                    lastGx = gx
                }

                delay(40)
            }
        }
    }
    // 编辑模式边框透明度动画
    val editBorderAlpha by animateFloatAsState(
        targetValue = if (isEditing) 1f else 0f,
        animationSpec = tween(300),
        label = "editBorder"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F23))
            .onSizeChanged { parentSize = it }
    ) {
        LayoutWidgets(viewModel, connectionState, parentSize, isEditing)

        Toolbar(
            connectionState = connectionState,
            connectionType = connectionType,
            isEditing = isEditing,
            onShowBtDialog = onShowBtDialog,
            onShowWifiDialog = onShowWifiDialog,
            onToggleEdit = { viewModel.toggleEditMode() },
            onSaveLayout = { viewModel.saveLayout(); viewModel.toggleEditMode() },
            onResetLayout = { viewModel.resetLayout() },
            onDisconnect = { viewModel.disconnect() },
            onShowCustomCmd = { showCustomCmd = true },
            onAddCustom = { showAddButton = true },
            onShowProtocolEditor = onShowProtocolEditor
        )

        // 添加自定义按钮对话框
        if (showAddButton) {
            AddCustomButtonDialog(
                onDismiss = { showAddButton = false },
                onAdd = { label, cmd, color ->
                    viewModel.addCustomButton(label, cmd, color)
                    showAddButton = false
                }
            )
        }

        // 自定义指令对话框
        if (showCustomCmd) {
            CustomCommandDialog(
                onSend = { cmd -> viewModel.sendCustomCommand(cmd) },
                onDismiss = { showCustomCmd = false }
            )
        }
    }
}

// ==================== 垂直滑块 ====================

@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = -1f..1f,
            modifier = Modifier
                .height(40.dp)
                .width(140.dp)
                .graphicsLayer { rotationZ = -90f },
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFAB47BC),
                activeTrackColor = Color(0xFFAB47BC),
                inactiveTrackColor = Color(0xFF333355)
            )
        )
    }
}

// ==================== 统一布局 ====================

@Composable
private fun BoxScope.LayoutWidgets(
    viewModel: MainViewModel,
    connectionState: UnifiedConnectionState,
    parentSize: IntSize,
    isEditing: Boolean
) {
    val layoutConfig by viewModel.layoutConfig.collectAsState()
    val carState by viewModel.carState.collectAsState()
    val haptic = rememberHaptic()

    val w = { id: String -> layoutConfig.widgets.find { it.id == id     }
}

// ==================== 带动画的按钮组件 ====================

    // ---- 移动摇杆 ----
    val moveW = w(WidgetIds.JOYSTICK_MOVE)
    if (moveW?.visible != false) {
        DraggableWidget(
            isEditing = isEditing,
            offsetX = moveW?.offsetX ?: 0.10f,
            offsetY = moveW?.offsetY ?: 0.50f,
            onOffsetChange = { dx, dy -> viewModel.updateWidgetPosition(WidgetIds.JOYSTICK_MOVE, dx, dy) },
            parentSize = parentSize
        ) {
            JoystickPad(size = (150 * (moveW?.scale ?: 1f)).dp, label = "移动(推幅=速度)", activeColor = Color(0xFF4FC3F7), onDragStart = { haptic.tick() }) { x, y ->
                viewModel.updateMoveJoystick(x, y)
            }
        }
    }

    // ---- 转向摇杆 ----
    val turnW = w(WidgetIds.JOYSTICK_TURN)
    if (turnW?.visible != false) {
        DraggableWidget(
            isEditing = isEditing,
            offsetX = turnW?.offsetX ?: 0.10f,
            offsetY = turnW?.offsetY ?: 0.12f,
            onOffsetChange = { dx, dy -> viewModel.updateWidgetPosition(WidgetIds.JOYSTICK_TURN, dx, dy) },
            parentSize = parentSize
        ) {
            JoystickPad(size = (120 * (turnW?.scale ?: 1f)).dp, label = "转向", activeColor = Color(0xFFFFD54F), onDragStart = { haptic.tick() }) { x, _ ->
                viewModel.updateTurnJoystick(x)
            }
        }
    }

    // ---- 夹爪升降 ----
    val upDownW = w(WidgetIds.SLIDER_GRIPPER_UPDOWN)
    if (upDownW?.visible != false) {
        DraggableWidget(
            isEditing = isEditing,
            offsetX = upDownW?.offsetX ?: 0.35f,
            offsetY = upDownW?.offsetY ?: 0.30f,
            onOffsetChange = { dx, dy -> viewModel.updateWidgetPosition(WidgetIds.SLIDER_GRIPPER_UPDOWN, dx, dy) },
            parentSize = parentSize
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("夹爪升降", color = Color(0xFF888899), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                VerticalSlider(
                    value = carState.gripperUpDown,
                    onValueChange = { viewModel.updateGripperUpDown(it) },
                    onValueChangeFinished = { viewModel.updateGripperUpDown(0f) },
                    modifier = Modifier.height(150.dp).width(50.dp)
                )
            }
        }
    }

    // ---- 夹爪开 ----
    val openW = w(WidgetIds.BUTTON_GRIPPER_OPEN)
    if (openW?.visible != false) {
        DraggableWidget(
            isEditing = isEditing,
            offsetX = openW?.offsetX ?: 0.33f,
            offsetY = openW?.offsetY ?: 0.68f,
            onOffsetChange = { dx, dy -> viewModel.updateWidgetPosition(WidgetIds.BUTTON_GRIPPER_OPEN, dx, dy) },
            parentSize = parentSize
        ) {
            GripperButton(
                text = "夹爪开",
                isActive = carState.gripperOpen,
                activeColor = Color(0xFF66BB6A),
                onClick = {
                    haptic.medium()
                    val open = !carState.gripperOpen
                    viewModel.setGripperOpen(open)
                    if (open) { viewModel.setGripperClose(false); viewModel.sendGripper(300, 0) }
                    else viewModel.sendGripper(0, 0)
                }
            )
        }
    }

    // ---- 夹爪关 ----
    val closeW = w(WidgetIds.BUTTON_GRIPPER_CLOSE)
    if (closeW?.visible != false) {
        DraggableWidget(
            isEditing = isEditing,
            offsetX = closeW?.offsetX ?: 0.42f,
            offsetY = closeW?.offsetY ?: 0.68f,
            onOffsetChange = { dx, dy -> viewModel.updateWidgetPosition(WidgetIds.BUTTON_GRIPPER_CLOSE, dx, dy) },
            parentSize = parentSize
        ) {
            GripperButton(
                text = "夹爪关",
                isActive = carState.gripperClose,
                activeColor = Color(0xFFEF5350),
                onClick = {
                    haptic.medium()
                    val close = !carState.gripperClose
                    viewModel.setGripperClose(close)
                    if (close) { viewModel.setGripperOpen(false); viewModel.sendGripper(-300, 0) }
                    else viewModel.sendGripper(0, 0)
                }
            )
        }
    }

    // ---- 查询 ----
    val queryW = w(WidgetIds.BUTTON_QUERY)
    if (queryW?.visible != false) {
        DraggableWidget(
            isEditing = isEditing,
            offsetX = queryW?.offsetX ?: 0.33f,
            offsetY = queryW?.offsetY ?: 0.80f,
            onOffsetChange = { dx, dy -> viewModel.updateWidgetPosition(WidgetIds.BUTTON_QUERY, dx, dy) },
            parentSize = parentSize
        ) {
            ActionButton(text = "查询", color = Color(0xFF4FC3F7)) { haptic.light(); viewModel.sendQuery() }
        }
    }

    // ---- 自动 ----
    val autoW = w(WidgetIds.BUTTON_AUTO_PLOT)
    if (autoW?.visible != false) {
        DraggableWidget(
            isEditing = isEditing,
            offsetX = autoW?.offsetX ?: 0.42f,
            offsetY = autoW?.offsetY ?: 0.80f,
            onOffsetChange = { dx, dy -> viewModel.updateWidgetPosition(WidgetIds.BUTTON_AUTO_PLOT, dx, dy) },
            parentSize = parentSize
        ) {
            ActionButton(text = "自动", color = Color(0xFFFFD54F)) { haptic.light(); viewModel.sendAutoPlot(true) }
        }
    }

    // ---- 急停 ----
    val stopW = w(WidgetIds.BUTTON_STOP)
    if (stopW?.visible != false) {
        DraggableWidget(
            isEditing = isEditing,
            offsetX = stopW?.offsetX ?: 0.51f,
            offsetY = stopW?.offsetY ?: 0.80f,
            onOffsetChange = { dx, dy -> viewModel.updateWidgetPosition(WidgetIds.BUTTON_STOP, dx, dy) },
            parentSize = parentSize
        ) {
            IconButton(
                onClick = { haptic.heavy(); viewModel.sendStop() },
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF333355)).padding(4.dp)
            ) { Icon(Icons.Default.Stop, "急停", tint = Color(0xFFEF5350)) }
        }
    }

    // ---- 状态显示 ----
    val statusW = w(WidgetIds.STATUS_DISPLAY)
    if (statusW?.visible != false) {
        DraggableWidget(
            isEditing = isEditing,
            offsetX = statusW?.offsetX ?: 0.72f,
            offsetY = statusW?.offsetY ?: 0.08f,
            onOffsetChange = { dx, dy -> viewModel.updateWidgetPosition(WidgetIds.STATUS_DISPLAY, dx, dy) },
            parentSize = parentSize
        ) {
            StatusDisplay(
                motorSpeeds = carState.motorSpeeds,
                lastReceived = carState.lastReceivedRaw,
                logMessages = carState.logMessages,
                connectionLabel = when (connectionState) {
                            UnifiedConnectionState.CONNECTED -> "已连接"
                            UnifiedConnectionState.CONNECTING -> "连接中..."
                            UnifiedConnectionState.ERROR -> "连接失败"
                            UnifiedConnectionState.DISCONNECTED -> "未连接"
                        },
                modifier = Modifier
                    .width((220 * (statusW?.scale ?: 1f)).dp)
                    .fillMaxHeight(0.88f)
            )
        }
    }

    // ---- 自定义按钮 ----
    val customWidgets = layoutConfig.widgets.filter { it.isCustom }
    for (widget in customWidgets) {
        DraggableWidget(
            isEditing = isEditing,
            offsetX = widget.offsetX,
            offsetY = widget.offsetY,
            onOffsetChange = { dx, dy -> viewModel.updateWidgetPosition(widget.id, dx, dy) },
            parentSize = parentSize
        ) {
            Box {
                Button(
                    onClick = { haptic.medium(); viewModel.sendCustomCommand(widget.command.orEmpty()) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(widget.colorHex).copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.graphicsLayer { scaleX = widget.scale; scaleY = widget.scale }
                ) {
                    Text(widget.label.orEmpty(), fontSize = 12.sp, color = Color.White)
                }

                // 编辑模式下显示删除按钮
                if (isEditing) {
                    IconButton(
                        onClick = { haptic.heavy(); viewModel.removeWidget(widget.id) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF5350))
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "删除",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GripperButton(
    text: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) activeColor else activeColor.copy(alpha = 0.6f),
        animationSpec = tween(200),
        label = "gripperColor"
    )
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.08f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "gripperScale"
    )

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = bgColor),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.graphicsLayer {
            scaleX = scale; scaleY = scale
        }
    ) { Text(text, fontSize = 12.sp, color = Color.White) }
}

@Composable
private fun ActionButton(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "btnScale"
    )

    Button(
        onClick = { pressed = true; onClick(); pressed = false },
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.graphicsLayer {
            scaleX = scale; scaleY = scale
        }
    ) { Text(text, fontSize = 12.sp, color = Color.White) }
}

// ==================== 工具栏 ====================

@Composable
private fun Toolbar(
    connectionState: UnifiedConnectionState,
    connectionType: ConnectionType,
    isEditing: Boolean,
    onShowBtDialog: () -> Unit,
    onShowWifiDialog: () -> Unit,
    onToggleEdit: () -> Unit,
    onSaveLayout: () -> Unit,
    onResetLayout: () -> Unit,
    onDisconnect: () -> Unit,
    onShowCustomCmd: () -> Unit,
    onAddCustom: () -> Unit,
    onShowProtocolEditor: () -> Unit
) {
    // 连接状态颜色动画
    val statusColor by animateColorAsState(
        targetValue = when (connectionState) {
            UnifiedConnectionState.CONNECTED -> Color(0xFF66BB6A)
            UnifiedConnectionState.CONNECTING -> Color(0xFFFFCA28)
            UnifiedConnectionState.ERROR -> Color(0xFFEF5350)
            UnifiedConnectionState.DISCONNECTED -> Color(0xFF4FC3F7)
        },
        animationSpec = tween(400),
        label = "statusColor"
    )

    val editIconTint by animateColorAsState(
        targetValue = if (isEditing) Color(0xFF00FF88) else Color(0xFF888899),
        animationSpec = tween(300),
        label = "editTint"
    )

    val haptic = rememberHaptic()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF16162E).copy(alpha = 0.95f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { haptic.light(); onShowBtDialog() }) {
                Icon(Icons.Default.Search, "连接蓝牙", tint = if (connectionType == ConnectionType.BLUETOOTH) statusColor else Color.Gray)
            }
            IconButton(onClick = { haptic.light(); onShowWifiDialog() }) {
                Icon(Icons.Default.Wifi, "连接WiFi", tint = if (connectionType == ConnectionType.WIFI) statusColor else Color.Gray)
            }
            // 状态文字 crossfade
            Crossfade(
                targetState = connectionState,
                animationSpec = tween(300),
                label = "statusText"
            ) { state ->
                Text(
                    when (state) {
                        UnifiedConnectionState.CONNECTED -> "已连接"
                        UnifiedConnectionState.CONNECTING -> "连接中..."
                        UnifiedConnectionState.ERROR -> "错误"
                        UnifiedConnectionState.DISCONNECTED -> "连接"
                    },
                    color = Color.White, fontSize = 13.sp
                )
            }
        }

        Text(
            when (connectionType) {
                ConnectionType.BLUETOOTH -> "蓝牙控制"
                ConnectionType.WIFI -> "WiFi控制"
                ConnectionType.NONE -> "无线控制"
            },
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        // 右侧
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { haptic.light(); onShowProtocolEditor() }) {
                Icon(Icons.Default.Edit, "协议编辑", tint = Color(0xFFAB47BC))
            }
            IconButton(onClick = { haptic.light(); onShowCustomCmd() }) {
                Icon(Icons.Default.Send, "发指令", tint = Color(0xFF4FC3F7))
            }
            IconButton(onClick = { haptic.tick(); onToggleEdit() }) {
                Icon(Icons.Default.Settings, "编辑布局", tint = editIconTint)
            }

            // 编辑模式按钮 - 水平展开/收起动画
            AnimatedVisibility(
                visible = isEditing,
                enter = expandHorizontally(tween(250)) + fadeIn(tween(250)),
                exit = shrinkHorizontally(tween(200)) + fadeOut(tween(200))
            ) {
                Row {
                    IconButton(onClick = { haptic.light(); onAddCustom() }) {
                        Icon(Icons.Default.Add, "添加按钮", tint = Color(0xFF66BB6A))
                    }
                    IconButton(onClick = { haptic.heavy(); onResetLayout() }) {
                        Icon(Icons.Default.Delete, "重置布局", tint = Color(0xFFFFCA28))
                    }
                    IconButton(onClick = { haptic.success(); onSaveLayout() }) {
                        Icon(Icons.Default.PlayArrow, "保存布局", tint = Color(0xFF00FF88))
                    }
                }
            }

            // 断开按钮 - 淡入淡出
            AnimatedVisibility(
                visible = connectionState == UnifiedConnectionState.CONNECTED,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(200))
            ) {
                IconButton(onClick = { haptic.heavy(); onDisconnect() }) {
                    Icon(Icons.Default.Close, "断开", tint = Color(0xFFEF5350))
                }
            }
        }
    }
}





