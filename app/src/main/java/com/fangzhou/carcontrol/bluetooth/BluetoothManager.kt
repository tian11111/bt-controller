package com.fangzhou.carcontrol.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager as SystemBluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

class BluetoothManager(private val context: Context) {

    companion object {
        private const val TAG = "BtManager"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        val BLE_NUS_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val BLE_NUS_TX_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val BLE_NUS_RX_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        val BLE_CUSTOM_SERVICE: UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
        val BLE_CUSTOM_TX: UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")
    }

    private val systemBtManager: SystemBluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? SystemBluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = systemBtManager?.adapter

    // Classic SPP
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    // BLE GATT
    private var gatt: BluetoothGatt? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var isBleConnection = false

    private var writeCallback: CancellableContinuation<Boolean>? = null
    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val writeMutex = Mutex()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _receivedData = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val receivedData: SharedFlow<String> = _receivedData.asSharedFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        if (!hasBluetoothPermission()) return
        _pairedDevices.value = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (_connectionState.value == ConnectionState.CONNECTING) return
        disconnect()
        _connectionState.value = ConnectionState.CONNECTING
        Log.i(TAG, "=== Connecting to ${device.name ?: "unknown"} (${device.address}) ===")

        when (device.type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC, BluetoothDevice.DEVICE_TYPE_DUAL -> connectClassic(device)
            BluetoothDevice.DEVICE_TYPE_LE -> connectBle(device)
            else -> connectClassic(device)
        }
    }

    // ==================== Classic SPP ====================

    @SuppressLint("MissingPermission")
    private fun connectClassic(device: BluetoothDevice) {
        scope.launch {
            var sock: BluetoothSocket? = null
            sock = tryConnect(device, "standard SPP") { device.createRfcommSocketToServiceRecord(SPP_UUID) }
            if (sock == null) sock = tryConnect(device, "rfcomm ch1") {
                device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(device, 1) as BluetoothSocket
            }
            if (sock == null) sock = tryConnect(device, "rfcomm ch3") {
                device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(device, 3) as BluetoothSocket
            }

            if (sock != null) {
                isBleConnection = false
                socket = sock
                inputStream = sock.inputStream
                outputStream = sock.outputStream
                _connectionState.value = ConnectionState.CONNECTED
                startClassicReadLoop()
            } else {
                connectBle(device)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun tryConnect(device: BluetoothDevice, method: String, factory: () -> BluetoothSocket): BluetoothSocket? {
        return withContext(Dispatchers.IO) {
            try {
                bluetoothAdapter?.cancelDiscovery()
                val sock = factory()
                withTimeout(8000) { sock.connect() }
                Log.i(TAG, "Connected via $method")
                sock
            } catch (e: Exception) {
                Log.w(TAG, "[$method] failed: ${e.message}")
                try { factory()?.close() } catch (_: Exception) {}
                null
            }
        }
    }

    // ==================== BLE GATT ====================

    @SuppressLint("MissingPermission")
    private fun connectBle(device: BluetoothDevice) {
        Log.i(TAG, "Connecting BLE GATT...")
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "BLE connected, requesting high priority...")
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                gatt.discoverServices()
            } else if (newState == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "BLE disconnected, status=$status")
                scope.launch {
                    _connectionState.value = if (_connectionState.value == ConnectionState.CONNECTING) ConnectionState.ERROR else ConnectionState.DISCONNECTED
                    cleanup()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                scope.launch { _connectionState.value = ConnectionState.ERROR; cleanup() }
                return
            }
            Log.i(TAG, "Services: ${gatt.services.map { it.uuid }}")

            if (findBleCharacteristics(gatt)) {
                isBleConnection = true
                this@BluetoothManager.gatt = gatt
                _connectionState.value = ConnectionState.CONNECTED
                enableRxNotification(gatt)
            } else {
                Log.e(TAG, "No usable characteristics found")
                scope.launch { _connectionState.value = ConnectionState.ERROR; cleanup() }
            }
        }

        // 关键：写完成回调，通知写队列可以写下一条了
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.d(TAG, "BLE write done, status=$status")
            writeCallback?.resume(status == BluetoothGatt.GATT_SUCCESS)
            writeCallback = null
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value
            if (data != null && data.isNotEmpty()) {
                val text = String(data, Charsets.UTF_8)
                Log.d(TAG, "BLE RX: $text")
                scope.launch { _receivedData.emit(text) }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val data = characteristic.value
                if (data != null && data.isNotEmpty()) {
                    scope.launch { _receivedData.emit(String(data, Charsets.UTF_8)) }
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.i(TAG, "Descriptor write status=$status")
        }
    }

    // ==================== Serialized transport write ====================

    /** 仅供ConnectionManager的统一调度器调用，本层不再保存业务发送队列。 */
    suspend fun writeFrame(data: ByteArray): Boolean = writeMutex.withLock {
        if (_connectionState.value != ConnectionState.CONNECTED) return@withLock false
        if (isBleConnection) writeBleFrame(data) else writeClassicFrame(data)
    }

    private suspend fun writeClassicFrame(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val output = outputStream ?: return@withContext false
            output.write(data)
            output.flush()
            true
        } catch (e: IOException) {
            Log.e(TAG, "Classic write error", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeBleFrame(data: ByteArray): Boolean {
        val g = gatt ?: return false
        val tx = txCharacteristic ?: return false
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + 20, data.size)
            val chunk = data.copyOfRange(offset, end)
            val success = withTimeoutOrNull(3000L) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    writeCallback = cont
                    cont.invokeOnCancellation {
                        if (writeCallback === cont) writeCallback = null
                    }
                    tx.value = chunk
                    tx.writeType = if (tx.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    } else {
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    }
                    if (!g.writeCharacteristic(tx)) {
                        if (writeCallback === cont) writeCallback = null
                        if (cont.isActive) cont.resume(false)
                    }
                }
            } ?: false
            if (!success) {
                Log.w(TAG, "BLE write failed/timeout for chunk")
                return false
            }
            offset = end
        }
        return true
    }

    private fun startClassicReadLoop() {
        readJob?.cancel()
        readJob = scope.launch {
            val buffer = ByteArray(1024)
            try {
                val input = inputStream ?: return@launch
                while (isActive) {
                    val count = input.read(buffer)
                    if (count > 0) {
                        val text = String(buffer, 0, count, Charsets.UTF_8)
                        Log.d(TAG, "Classic RX: $text")
                        _receivedData.emit(text)
                    } else if (count < 0) {
                        Log.w(TAG, "Classic stream closed")
                        break
                    }
                }
            } catch (e: IOException) {
                if (isActive) {
                    Log.e(TAG, "Classic read error", e)
                }
            } finally {
                if (isActive) {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    cleanup()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun findBleCharacteristics(gatt: BluetoothGatt): Boolean {
        // Nordic UART
        gatt.getService(BLE_NUS_UUID)?.let { svc ->
            txCharacteristic = svc.getCharacteristic(BLE_NUS_TX_UUID)
            rxCharacteristic = svc.getCharacteristic(BLE_NUS_RX_UUID)
            if (txCharacteristic != null && rxCharacteristic != null) {
                Log.i(TAG, "Found Nordic UART"); return true
            }
        }

        // FFE0 (CC41-A / HM-10)
        gatt.getService(BLE_CUSTOM_SERVICE)?.let { svc ->
            Log.i(TAG, "FFE0 chars: ${svc.characteristics.map { "${it.uuid} props=${it.properties}" }}")
            for (char in svc.characteristics) {
                val props = char.properties
                if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                    props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                    txCharacteristic = char
                }
                if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                    rxCharacteristic = char
                }
            }
            if (txCharacteristic != null) {
                if (rxCharacteristic == null) rxCharacteristic = txCharacteristic
                Log.i(TAG, "Found FFE0: TX=${txCharacteristic?.uuid} RX=${rxCharacteristic?.uuid}")
                return true
            }
        }

        // 遍历所有非标准服务
        for (svc in gatt.services) {
            if (svc.uuid.toString().startsWith("0000180")) continue
            for (char in svc.characteristics) {
                val props = char.properties
                if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                    props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                    txCharacteristic = char
                    rxCharacteristic = svc.characteristics.firstOrNull {
                        it.uuid != char.uuid && (it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
                    } ?: char
                    Log.i(TAG, "Found generic: TX=${char.uuid}")
                    return true
                }
            }
        }
        return false
    }

    @SuppressLint("MissingPermission")
    private fun enableRxNotification(gatt: BluetoothGatt) {
        val rx = rxCharacteristic ?: return
        gatt.setCharacteristicNotification(rx, true)
        val cccd = rx.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"))
        if (cccd != null) {
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(cccd)
            Log.i(TAG, "CCCD notification enabled on ${rx.uuid}")
        }
    }

    // ==================== Send ====================

    fun send(data: String): Boolean = sendBytes(data.toByteArray(Charsets.UTF_8))

    fun sendBytes(data: ByteArray): Boolean {
        if (_connectionState.value != ConnectionState.CONNECTED) return false
        scope.launch { writeFrame(data) }
        return true
    }

    // ==================== Disconnect ====================

    fun disconnect() {
        readJob?.cancel()
        cleanup()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    @SuppressLint("MissingPermission")
    private fun cleanup() {
        try { inputStream?.close() } catch (_: IOException) {}
        try { outputStream?.close() } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        try { gatt?.disconnect() } catch (_: Exception) {}
        try { gatt?.close() } catch (_: Exception) {}
        inputStream = null; outputStream = null; socket = null
        gatt = null; txCharacteristic = null; rxCharacteristic = null
        isBleConnection = false
        writeCallback?.resume(false)
        writeCallback = null
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }
}
