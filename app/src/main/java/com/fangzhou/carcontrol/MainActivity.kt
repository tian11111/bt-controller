package com.fangzhou.carcontrol

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.fangzhou.carcontrol.ui.BtConnectionDialog
import com.fangzhou.carcontrol.ui.ControlPanel
import com.fangzhou.carcontrol.ui.ProtocolEditorScreen

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            viewModel.btManager.refreshPairedDevices()
        } else {
            Toast.makeText(this, "需要蓝牙权限才能使用", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        requestBtPermissions()

        setContent {
            val connectionState by viewModel.btManager.connectionState.collectAsState()
            var showBtDialog by remember { mutableStateOf(false) }
            var showProtocolEditor by remember { mutableStateOf(false) }

            if (showProtocolEditor) {
                ProtocolEditorScreen(
                    store = viewModel.protocolStore,
                    onBack = { showProtocolEditor = false },
                    onSendCommand = { cmd -> viewModel.sendCustomCommand(cmd) }
                )
            } else {
                ControlPanel(
                    viewModel = viewModel,
                    connectionState = connectionState,
                    onShowBtDialog = {
                        if (checkBtPermissions()) {
                            viewModel.btManager.refreshPairedDevices()
                            showBtDialog = true
                        } else {
                            requestBtPermissions()
                        }
                    },
                    onShowProtocolEditor = { showProtocolEditor = true }
                )
            }

            if (showBtDialog) {
                val devices by viewModel.btManager.pairedDevices.collectAsState()
                BtConnectionDialog(
                    devices = devices,
                    onRefresh = { viewModel.btManager.refreshPairedDevices() },
                    onConnect = { device -> viewModel.connectDevice(device) },
                    onDismiss = { showBtDialog = false }
                )
            }
        }
    }

    private fun checkBtPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun requestBtPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        val needRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needRequest.isNotEmpty()) {
            permissionLauncher.launch(needRequest.toTypedArray())
        } else {
            viewModel.btManager.refreshPairedDevices()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        if (checkBtPermissions()) {
            viewModel.btManager.refreshPairedDevices()
        }
    }
}
