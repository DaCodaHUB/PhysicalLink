package com.dacodahub.physicallink.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class SensorReadings(
    val soil: String = "--",
    val temperature: String = "--",
    val humidity: String = "--",
    val pressure: String = "--"
)

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Discovering : ConnectionState
    data object Connected : ConnectionState
    data object ConnectionLost : ConnectionState
    data class Error(val message: String) : ConnectionState
}

@SuppressLint("MissingPermission")
class BleConnectionManager(private val context: Context) {
    companion object {
        private fun uuid(short: String) =
            UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")

        private val SERVICE_UUID = uuid("1234")
        private val SOIL_UUID = uuid("5678")
        private val TEMP_UUID = uuid("5679")
        private val HUMIDITY_UUID = uuid("567a")
        private val PRESSURE_UUID = uuid("567b")
        private val CCCD_UUID = uuid("2902")
        private val CAPABILITIES_UUID = uuid("5670")
        private val SENSOR_UUIDS = listOf(SOIL_UUID, TEMP_UUID, HUMIDITY_UUID, PRESSURE_UUID)
        private const val OPERATION_TIMEOUT_MS = 5_000L
        private const val CONNECTION_TIMEOUT_MS = 30_000L
        private const val DISCONNECT_TIMEOUT_MS = 5_000L
    }

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    // GATT, queue, timeout and StateFlow mutations are confined to the main looper.
    private var gatt: BluetoothGatt? = null
    private var disconnectingGatt: BluetoothGatt? = null
    private var disconnectTimeout: Runnable? = null
    private var linkConnected = false
    private var timeoutRunnable: Runnable? = null
    private var activeCharacteristic: BluetoothGattCharacteristic? = null
    private var activeDescriptor: BluetoothGattDescriptor? = null
    private var startFailure: String? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()
    private val _readings = MutableStateFlow(SensorReadings())
    val readings = _readings.asStateFlow()
    private val _capabilities = MutableStateFlow<DeviceCapabilities?>(null)
    val capabilities = _capabilities.asStateFlow()

    private val operationQueue: GattOperationQueue = GattOperationQueue(
        execute = { operation ->
            startFailure = null
            val activeGatt = gatt
            val started = try {
                checkNotNull(activeGatt) { "No active GATT connection" }
                executeOperation(activeGatt, operation)
            } catch (e: SecurityException) {
                startFailure = "Bluetooth permission denied"
                false
            } catch (e: IllegalStateException) {
                startFailure = e.message
                false
            }
            if (started && activeGatt != null) {
                scheduleTimeout(OPERATION_TIMEOUT_MS) {
                    if (gatt === activeGatt && operationQueue.current === operation) {
                        operationQueue.timeout(operation)
                    }
                }
            }
            started
        },
        onFailure = { operation, reason ->
            val message = "$operation: ${startFailure ?: reason}"
            Log.e("PhysicalLink", message)
            endSession(ConnectionState.Error(message))
        }
    )

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == handler.looper) block() else handler.post { block() }
    }

    private fun scheduleTimeout(delayMs: Long, block: () -> Unit) {
        cancelTimeout()
        val runnable = Runnable {
            timeoutRunnable = null
            block()
        }
        timeoutRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let(handler::removeCallbacks)
        timeoutRunnable = null
    }

    private fun executeOperation(activeGatt: BluetoothGatt, operation: GattOperation): Boolean {
        val service = checkNotNull(activeGatt.getService(SERVICE_UUID)) {
            "PhysicalLink service not found"
        }
        val characteristicUuid = when (operation) {
            is GattOperation.ReadCharacteristic -> operation.characteristicUuid
            is GattOperation.EnableNotifications -> operation.characteristicUuid
        }
        val characteristic = checkNotNull(service.getCharacteristic(characteristicUuid)) {
            "Sensor characteristic $characteristicUuid not found"
        }
        activeCharacteristic = characteristic
        activeDescriptor = null
        return when (operation) {
            is GattOperation.ReadCharacteristic -> activeGatt.readCharacteristic(characteristic)
            is GattOperation.EnableNotifications -> {
                val descriptor = checkNotNull(characteristic.getDescriptor(CCCD_UUID)) {
                    "Notification descriptor missing for $characteristicUuid"
                }
                activeDescriptor = descriptor
                if (!activeGatt.setCharacteristicNotification(characteristic, true)) return false
                if (Build.VERSION.SDK_INT >= 33) {
                    activeGatt.writeDescriptor(
                        descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    ) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    activeGatt.writeDescriptor(descriptor)
                }
            }
        }
    }

    fun connect(address: String) = onMain {
        endSession(ConnectionState.Disconnected)
        _connectionState.value = ConnectionState.Connecting
        try {
            val adapter = checkNotNull(bluetoothManager.adapter) { "Bluetooth unavailable" }
            check(adapter.isEnabled) { "Bluetooth is disabled" }
            // Always post callbacks, even when delivered on main, so assignment
            // completes before a connection callback examines the session.
            val device = adapter.getRemoteDevice(address)
            gatt = if (Build.VERSION.SDK_INT >= 37) {
                val settings = BluetoothGattConnectionSettings.Builder()
                    .setAutoConnectEnabled(false)
                    .setTransport(BluetoothDevice.TRANSPORT_LE)
                    .setAutomaticMtuEnabled(false)
                    .build()
                device.connectGatt(settings, { command -> handler.post(command) }, callback)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            }
            val activeGatt = checkNotNull(gatt) { "Could not create GATT connection" }
            scheduleTimeout(CONNECTION_TIMEOUT_MS) {
                if (gatt === activeGatt) endSession(ConnectionState.Error("Connection timed out"))
            }
        } catch (e: SecurityException) {
            endSession(ConnectionState.Error("Bluetooth permission denied"))
        } catch (e: IllegalArgumentException) {
            endSession(ConnectionState.Error("Invalid Bluetooth address"))
        } catch (e: IllegalStateException) {
            endSession(ConnectionState.Error(e.message ?: "Could not connect"))
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(callbackGatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(
                "PhysicalLink",
                "GATT callback: status=$status, newState=$newState"
            )

            handler.post {
                if (callbackGatt === disconnectingGatt) {
                    if (
                        newState == BluetoothProfile.STATE_DISCONNECTED ||
                        status != BluetoothGatt.GATT_SUCCESS
                    ) {
                        Log.d("PhysicalLink", "Graceful disconnect confirmed")
                        finishDisconnect(callbackGatt)
                    }
                    return@post
                }

                if (callbackGatt !== gatt) {
                    Log.d("PhysicalLink", "Ignoring callback from old GATT")
                    return@post
                }
                if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                    val state = if (linkConnected) ConnectionState.ConnectionLost
                    else if (status != BluetoothGatt.GATT_SUCCESS) ConnectionState.Error("Connection failed: $status")
                    else ConnectionState.Disconnected
                    endSession(state)
                } else if (newState == BluetoothProfile.STATE_CONNECTED && !linkConnected) {
                    linkConnected = true
                    _connectionState.value = ConnectionState.Discovering
                    try {
                        if (!callbackGatt.discoverServices()) {
                            endSession(ConnectionState.Error("Could not start discovery"))
                        } else {
                            scheduleTimeout(CONNECTION_TIMEOUT_MS) {
                                if (gatt === callbackGatt) {
                                    endSession(ConnectionState.Error("Service discovery timed out"))
                                }
                            }
                        }
                    } catch (e: SecurityException) {
                        endSession(ConnectionState.Error("Bluetooth permission denied"))
                    }
                }
            }
        }

        override fun onServicesDiscovered(callbackGatt: BluetoothGatt, status: Int) {
            handler.post {
                if (callbackGatt !== gatt || _connectionState.value != ConnectionState.Discovering) return@post
                cancelTimeout()
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    endSession(ConnectionState.Error("Service discovery failed: $status"))
                    return@post
                }
                if (callbackGatt.getService(SERVICE_UUID) == null) {
                    endSession(ConnectionState.Error("PhysicalLink service not found"))
                    return@post
                }
                _connectionState.value = ConnectionState.Connected
                // Each target is used once per operation type in this session.
                // Android callbacks carry no request ID; never retry a timed-out
                // request on the same GATT, where a late callback is ambiguous.
                operationQueue.enqueue(
                    listOf(GattOperation.ReadCharacteristic(CAPABILITIES_UUID)) +
                            SENSOR_UUIDS.map { GattOperation.ReadCharacteristic(it) } +
                            SENSOR_UUIDS.map { GattOperation.EnableNotifications(it) }
                )
            }
        }

        override fun onCharacteristicRead(
            callbackGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic,
            value: ByteArray, status: Int
        ) = handleRead(callbackGatt, characteristic, value.copyOf(), status)

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            callbackGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
        ) {
            if (Build.VERSION.SDK_INT < 33) {
                handleRead(callbackGatt, characteristic, characteristic.value?.copyOf() ?: byteArrayOf(), status)
            }
        }

        override fun onCharacteristicChanged(
            callbackGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray
        ) = handleNotification(callbackGatt, characteristic, value.copyOf())

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            callbackGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < 33) {
                handleNotification(callbackGatt, characteristic, characteristic.value?.copyOf() ?: byteArrayOf())
            }
        }

        override fun onDescriptorWrite(
            callbackGatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int
        ) {
            handler.post {
                if (callbackGatt !== gatt || descriptor !== activeDescriptor || descriptor.uuid != CCCD_UUID) return@post
                val operation = operationQueue.current as? GattOperation.EnableNotifications ?: return@post
                if (operation.characteristicUuid != descriptor.characteristic.uuid) return@post
                completeOperation(operation, status)
            }
        }
    }

    private fun handleRead(
        callbackGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int
    ) {
        handler.post {
            if (callbackGatt !== gatt || characteristic !== activeCharacteristic) return@post
            val operation = operationQueue.current as? GattOperation.ReadCharacteristic ?: return@post
            if (operation.characteristicUuid != characteristic.uuid) return@post
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.uuid == CAPABILITIES_UUID) {
                    val json = value.toString(Charsets.UTF_8)

                    try {
                        val parsed = DeviceCapabilitiesParser.parse(json)
                        _capabilities.value = parsed

                        Log.d(
                            "PhysicalLink",
                            "Discovered ${parsed.capabilities.size} capabilities from ${parsed.device}"
                        )
                    } catch (e: Exception) {
                        Log.e("PhysicalLink", "Invalid capability description", e)
                        endSession(
                            ConnectionState.Error("Invalid capability description")
                        )
                        return@post
                    }
                } else {
                    updateReading(characteristic.uuid, value)
                }
            }

            completeOperation(operation, status)
        }
    }

    private fun completeOperation(operation: GattOperation, status: Int) {
        cancelTimeout()
        activeCharacteristic = null
        activeDescriptor = null
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.e("PhysicalLink", "$operation callback failed: status=$status")
            startFailure = "GATT callback failed: status=$status"
        }
        operationQueue.complete(operation, status == BluetoothGatt.GATT_SUCCESS)
    }

    private fun handleNotification(
        callbackGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray
    ) {
        handler.post {
            if (callbackGatt !== gatt || characteristic.service?.uuid != SERVICE_UUID) return@post
            updateReading(characteristic.uuid, value)
        }
    }

    private fun updateReading(uuid: UUID, value: ByteArray) {
        val text = value.toString(Charsets.UTF_8)
        _readings.value = when (uuid) {
            SOIL_UUID -> _readings.value.copy(soil = text)
            TEMP_UUID -> _readings.value.copy(temperature = text)
            HUMIDITY_UUID -> _readings.value.copy(humidity = text)
            PRESSURE_UUID -> _readings.value.copy(pressure = text)
            else -> _readings.value
        }
    }

    fun clearConnectionError() = onMain {
        if (_connectionState.value is ConnectionState.ConnectionLost || _connectionState.value is ConnectionState.Error) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    fun disconnect() = onMain {
        val oldGatt = gatt

        if (oldGatt == null) {
            endSession(ConnectionState.Disconnected)
            return@onMain
        }

        cancelTimeout()
        operationQueue.clear()
        activeCharacteristic = null
        activeDescriptor = null

        gatt = null
        linkConnected = false
        disconnectingGatt = oldGatt

        _readings.value = SensorReadings()
        _capabilities.value = null
        _connectionState.value = ConnectionState.Disconnected

        try {
            Log.d("PhysicalLink", "Requesting graceful GATT disconnect")
            oldGatt.disconnect()

            val fallback = Runnable {
                if (disconnectingGatt === oldGatt) {
                    Log.w("PhysicalLink", "Disconnect timed out; closing GATT")
                    finishDisconnect(oldGatt)
                }
            }

            disconnectTimeout = fallback
            handler.postDelayed(fallback, DISCONNECT_TIMEOUT_MS)

        } catch (e: SecurityException) {
            Log.w("PhysicalLink", "Cannot disconnect", e)
            finishDisconnect(oldGatt)
        }
    }

    private fun finishDisconnect(oldGatt: BluetoothGatt) {
        if (disconnectingGatt !== oldGatt) return

        disconnectTimeout?.let(handler::removeCallbacks)
        disconnectTimeout = null
        disconnectingGatt = null

        try {
            oldGatt.close()
        } catch (e: SecurityException) {
            Log.w("PhysicalLink", "Cannot close GATT", e)
        }

        Log.d("PhysicalLink", "GATT cleanup completed")
        val device = bluetoothManager.adapter.getRemoteDevice(oldGatt.device.address)

        val devices = bluetoothManager.getConnectedDevices(
            BluetoothProfile.GATT
        )
    }

    private fun endSession(state: ConnectionState) {
        Log.d("PhysicalLink", "endSession called: $state")
        cancelTimeout()
        operationQueue.clear()
        activeCharacteristic = null
        activeDescriptor = null
        linkConnected = false
        val oldGatt = gatt
        gatt = null // Invalidate before disconnect/close can deliver callbacks.
        if (oldGatt != null) {
            try {
                Log.d("PhysicalLink", "Requesting GATT disconnect")
                oldGatt.disconnect()
            } catch (e: SecurityException) {
                Log.w("PhysicalLink", "Cannot disconnect", e)
                oldGatt.close()
            }
        }

        _readings.value = SensorReadings()
        _capabilities.value = null
        _connectionState.value = state
    }
}
