package com.fangzhou.carcontrol.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager as SystemBluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

class BluetoothManager(private val context: Context) {

    companion object {
        private const val TAG = "BtManager"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val systemBtManager: SystemBluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? SystemBluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = systemBtManager?.adapter

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _receivedData = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val receivedData: SharedFlow<String> = _receivedData.asSharedFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun hasBluetoothPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        if (!hasBluetoothPermission()) return
        val devices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        _pairedDevices.value = devices
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (_connectionState.value == ConnectionState.CONNECTING) return

        disconnect()
        _connectionState.value = ConnectionState.CONNECTING

        scope.launch {
            try {
                val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothAdapter?.cancelDiscovery()

                sock.connect()

                socket = sock
                inputStream = sock.inputStream
                outputStream = sock.outputStream
                _connectionState.value = ConnectionState.CONNECTED

                Log.i(TAG, "Connected to ${device.name ?: device.address}")
                startReading()
            } catch (e: IOException) {
                Log.e(TAG, "Connection failed", e)
                _connectionState.value = ConnectionState.ERROR
                cleanup()
            }
        }
    }

    fun disconnect() {
        readJob?.cancel()
        cleanup()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun cleanup() {
        try {
            inputStream?.close()
        } catch (_: IOException) {}
        try {
            outputStream?.close()
        } catch (_: IOException) {}
        try {
            socket?.close()
        } catch (_: IOException) {}
        inputStream = null
        outputStream = null
        socket = null
    }

    private fun startReading() {
        readJob = scope.launch {
            val buffer = ByteArray(1024)
            try {
                while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                    val len = inputStream?.read(buffer) ?: -1
                    if (len > 0) {
                        val text = String(buffer, 0, len, Charsets.UTF_8)
                        _receivedData.emit(text)
                    } else if (len == -1) {
                        break
                    }
                }
            } catch (e: IOException) {
                if (isActive) {
                    Log.e(TAG, "Read error", e)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    if (_connectionState.value == ConnectionState.CONNECTED) {
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }
                }
            }
        }
    }

    fun send(data: String): Boolean {
        return try {
            val os = outputStream ?: return false
            os.write(data.toByteArray(Charsets.UTF_8))
            os.flush()
            true
        } catch (e: IOException) {
            Log.e(TAG, "Send failed", e)
            false
        }
    }

    fun sendBytes(data: ByteArray): Boolean {
        return try {
            val os = outputStream ?: return false
            os.write(data)
            os.flush()
            true
        } catch (e: IOException) {
            Log.e(TAG, "Send failed", e)
            false
        }
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }
}
