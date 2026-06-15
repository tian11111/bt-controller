package com.fangzhou.carcontrol.ui

import androidx.compose.runtime.*
import com.fangzhou.carcontrol.MainViewModel
import com.fangzhou.carcontrol.connection.UnifiedConnectionState

@Composable
fun BluetoothControlScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val connectionState by viewModel.connectionManager.unifiedState.collectAsState()
    val connectionType by viewModel.connectionManager.connectionType.collectAsState()
    var showBtDialog by remember { mutableStateOf(false) }
    var showProtocolEditor by remember { mutableStateOf(false) }

    // 主控制面板
    ControlPanel(
        viewModel = viewModel,
        connectionState = connectionState,
        connectionType = connectionType,
        onShowBtDialog = { showBtDialog = true },
        onShowWifiDialog = { /* 蓝牙模式不显示 WiFi */ },
        onShowProtocolEditor = { showProtocolEditor = true }
    )

    // 蓝牙连接对话框
    if (showBtDialog) {
        val devices by viewModel.connectionManager.btManager.pairedDevices.collectAsState()
        BtConnectionDialog(
            devices = devices,
            onRefresh = { viewModel.connectionManager.btManager.refreshPairedDevices() },
            onConnect = { device -> 
                viewModel.connectionManager.connectBluetooth(device)
                // 不关闭对话框，等连接结果
            },
            onDismiss = { 
                showBtDialog = false
            }
        )
    }

    // 连接成功后自动关闭对话框
    LaunchedEffect(connectionState) {
        if (connectionState == UnifiedConnectionState.CONNECTED && showBtDialog) {
            showBtDialog = false
        }
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
