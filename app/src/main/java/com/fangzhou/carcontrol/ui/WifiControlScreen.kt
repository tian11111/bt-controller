package com.fangzhou.carcontrol.ui

import androidx.compose.runtime.*
import com.fangzhou.carcontrol.MainViewModel
import com.fangzhou.carcontrol.connection.UnifiedConnectionState

@Composable
fun WifiControlScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val connectionState by viewModel.connectionManager.unifiedState.collectAsState()
    val connectionType by viewModel.connectionManager.connectionType.collectAsState()
    var showWifiDialog by remember { mutableStateOf(true) }
    var showProtocolEditor by remember { mutableStateOf(false) }

    // 主控制面板
    ControlPanel(
        viewModel = viewModel,
        connectionState = connectionState,
        connectionType = connectionType,
        onShowBtDialog = { /* WiFi 模式不显示蓝牙 */ },
        onShowWifiDialog = { showWifiDialog = true },
        onShowProtocolEditor = { showProtocolEditor = true }
    )

    // WiFi 连接对话框
    if (showWifiDialog) {
        WifiConnectionDialog(
            onConnect = { config -> 
                viewModel.connectionManager.connectWifi(config)
                showWifiDialog = false
            },
            onDismiss = { 
                showWifiDialog = false
                // 只有在未连接状态下点取消才返回模式选择
                if (connectionState == UnifiedConnectionState.DISCONNECTED) {
                    onBack()
                }
            }
        )
    }

    // 协议编辑器对话框
    if (showProtocolEditor) {
        ProtocolEditorScreen(
            store = viewModel.protocolStore,
            onBack = { showProtocolEditor = false },
            onSendCommand = { cmd -> viewModel.sendCustomCommand(cmd) }
        )
    }
}
