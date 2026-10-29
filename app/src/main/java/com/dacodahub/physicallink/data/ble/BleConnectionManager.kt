package com.dacodahub.physicallink.data.ble

import com.dacodahub.physicallink.domain.model.ConnectionState
import com.dacodahub.physicallink.domain.model.DeviceCapabilities
import com.dacodahub.physicallink.domain.model.DeviceCapability
import com.dacodahub.physicallink.domain.model.dashboardDeviceId
import com.dacodahub.physicallink.domain.model.dashboardCapabilities
import android.Manifest
import android.content.pm.PackageManager
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

@SuppressLint("MissingPermission")
class BleConnectionManager(
    private val context: Context,
    private val onSensorReading: (DeviceCapability, String) -> Unit = { _, _ -> },
    private val onSessionStarted: (String) -> Unit = {},
    private val onSessionEnded: () -> Unit = {},
    private val onV2Reading: (DeviceCapability, String, Long) -> Unit = { _, _, _ -> }
) {
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
        private const val OPERATION_TIMEOUT_MS = 5_000L
        private const val CONNECTION_TIMEOUT_MS = 30_000L
        private const val DISCONNECT_TIMEOUT_MS = 5_000L
    }

    private val bluetoothManager: BluetoothManager? = context.getSystemService(BluetoothManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    // GATT, queue, timeout and StateFlow mutations are confined to the main looper.
    private var gatt: BluetoothGatt? = null
    private var disconnectingGatt: BluetoothGatt? = null
    private var disconnectTimeout: Runnable? = null
    private var live: V2LiveSession? = null
    private var recoveryTick: Runnable? = null
    private var lastCaptureAt = 0L
    private val _v2Live = MutableStateFlow(com.dacodahub.physicallink.domain.model.V2LiveState())
    val v2Live = _v2Live.asStateFlow()
    private var dashboardUpdates = com.dacodahub.physicallink.domain.model.V2DashboardUpdates()
    private val _dashboardDeviceId = MutableStateFlow<String?>(null)
    val dashboardDeviceId = _dashboardDeviceId.asStateFlow()
    private var v2 = false
    private var transfer: V2MetadataTransfer? = null
    private var commandDeadline = 0L
    private var setupTimeout: Runnable? = null
    private val _v2Metadata = MutableStateFlow<com.dacodahub.physicallink.domain.model.V2Metadata?>(null)
    val v2Metadata = _v2Metadata.asStateFlow()
    private var linkConnected = false
    private var timeoutRunnable: Runnable? = null
    private var activeCharacteristic: BluetoothGattCharacteristic? = null
    private var activeDescriptor: BluetoothGattDescriptor? = null
    private var startFailure: String? = null
    private var pendingCapabilities: DeviceCapabilities? = null
    private var sensorCapabilitiesByUuid = emptyMap<UUID, DeviceCapability>()
    private val _deviceId = MutableStateFlow<String?>(null)
    val deviceId = _deviceId.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()
    private val _readings = MutableStateFlow(SensorReadings())
    val readings = _readings.asStateFlow()
    private val _capabilities = MutableStateFlow<DeviceCapabilities?>(null)
    val capabilities = _capabilities.asStateFlow()
    private val _sensorValues =
        MutableStateFlow<Map<String, String>>(emptyMap())

    val sensorValues = _sensorValues.asStateFlow()

    private val operationQueue: GattOperationQueue = GattOperationQueue(
        execute = { operation ->
            startFailure = null
            val activeGatt = gatt
            val started = try {
                checkNotNull(activeGatt) { "No active GATT connection" }
                executeOperation(activeGatt, operation)
            } catch (e: SecurityException) {
                startFailure = "Bluetooth permission denied. Allow Nearby devices in app settings, then reconnect."
                false
            } catch (e: IllegalStateException) {
                startFailure = e.message
                false
            }
            if (started && activeGatt != null) {
                val commandRead = operation is GattOperation.ReadCharacteristic &&
                    (operation.characteristicUuid == V2Protocol.STATE ||
                        (operation.characteristicUuid == V2Protocol.METADATA && (transfer?.nextPage ?: 0) > 0))
                val operationTimeout = if (v2 && commandRead)
                    minOf(OPERATION_TIMEOUT_MS, (commandDeadline - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(1))
                else OPERATION_TIMEOUT_MS
                scheduleTimeout(operationTimeout) {
                    if (gatt === activeGatt && operationQueue.current === operation) {
                        operationQueue.timeout(operation)
                    }
                }
            }
            started
        },
        onIdle = {
            val ready = pendingCapabilities
            if (gatt != null && _connectionState.value == ConnectionState.Discovering && ready != null) {
                setupTimeout?.let(handler::removeCallbacks)
                setupTimeout = null
                pendingCapabilities = null
                _capabilities.value = ready
                _connectionState.value = ConnectionState.Connected
            }
        },
        onFailure = { operation, reason ->
            val detail = startFailure ?: reason
            Log.e("PhysicalLink", "$operation: $detail")
            endSession(ConnectionState.Error("Sensor setup failed: $detail. Check device power and Bluetooth, then reconnect."))
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
        val service = checkNotNull(activeGatt.getService(if (v2) V2Protocol.SERVICE else SERVICE_UUID)) {
            "PhysicalLink service not found"
        }
        val characteristicUuid = when (operation) {
            is GattOperation.WriteControl -> V2Protocol.CONTROL
            is GattOperation.ReadCharacteristic -> operation.characteristicUuid
            is GattOperation.EnableNotifications -> operation.characteristicUuid
        }
        val characteristic = checkNotNull(service.getCharacteristic(characteristicUuid)) {
            "Sensor characteristic $characteristicUuid not found"
        }
        activeCharacteristic = characteristic
        activeDescriptor = null
        return when (operation) {
            is GattOperation.WriteControl -> {
                commandDeadline = android.os.SystemClock.elapsedRealtime() + 5_000
                if (Build.VERSION.SDK_INT >= 33) activeGatt.writeCharacteristic(characteristic, operation.value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
                else {
                    @Suppress("DEPRECATION")
                    characteristic.value = operation.value
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    activeGatt.writeCharacteristic(characteristic)
                }
            }
            is GattOperation.ReadCharacteristic -> activeGatt.readCharacteristic(characteristic)
            is GattOperation.EnableNotifications -> {
                val enableValue = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                val descriptor = checkNotNull(characteristic.getDescriptor(CCCD_UUID)) {
                    "Notification descriptor missing for $characteristicUuid"
                }
                activeDescriptor = descriptor
                if (!activeGatt.setCharacteristicNotification(characteristic, true)) return false
                if (Build.VERSION.SDK_INT >= 33) {
                    activeGatt.writeDescriptor(
                        descriptor, enableValue
                    ) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = enableValue
                    @Suppress("DEPRECATION")
                    activeGatt.writeDescriptor(descriptor)
                }
            }
        }
    }

    fun connect(address: String) = onMain {
        if (gatt != null && _deviceId.value == address) return@onMain
        endSession(ConnectionState.Disconnected)
        _deviceId.value = address
        _dashboardDeviceId.value = null
        dashboardUpdates = com.dacodahub.physicallink.domain.model.V2DashboardUpdates()
        _connectionState.value = ConnectionState.Connecting
        try {
            val adapter = checkNotNull(bluetoothManager?.adapter) { "Bluetooth unavailable" }
            check(adapter.isEnabled) { "Bluetooth is disabled. Turn it on, then reconnect." }
            // Always post callbacks, even when delivered on main, so assignment
            // completes before a connection callback examines the session.
            val device = adapter.getRemoteDevice(address)
            _dashboardDeviceId.value = address
            onSessionStarted(address)
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
                if (gatt === activeGatt) endSession(ConnectionState.Error("Connection timed out. Move the device closer and try again."))
            }
        } catch (e: SecurityException) {
            endSession(ConnectionState.Error("Bluetooth permission denied. Allow Nearby devices in app settings, then reconnect."))
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
                    else if (status != BluetoothGatt.GATT_SUCCESS) ConnectionState.Error("Connection failed (code $status). Check device power, move closer, then reconnect.")
                    else ConnectionState.Error("Device disconnected before connection completed. Check device power and range, then retry.")
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
                        endSession(ConnectionState.Error("Bluetooth permission denied. Allow Nearby devices in app settings, then reconnect."))
                    }
                }
            }
        }

        override fun onServicesDiscovered(callbackGatt: BluetoothGatt, status: Int) {
            handler.post {
                if (callbackGatt !== gatt || _connectionState.value != ConnectionState.Discovering || operationQueue.current != null) return@post
                cancelTimeout()
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    endSession(ConnectionState.Error("Service discovery failed: $status"))
                    return@post
                }
                val experimental = callbackGatt.getService(V2Protocol.SERVICE)
                if (experimental != null) {
                    v2 = true
                    val metadata = experimental.getCharacteristic(V2Protocol.METADATA)
                    val control = experimental.getCharacteristic(V2Protocol.CONTROL)
                    val state = experimental.getCharacteristic(V2Protocol.STATE)
                    if (metadata == null || metadata.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0 ||
                        control == null || control.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0 ||
                        state == null || state.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0 ||
                        state.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0 || state.getDescriptor(CCCD_UUID) == null) {
                        endSession(ConnectionState.Error("Invalid v2 service: Metadata READ, Control WRITE with response, and State/Live READ + NOTIFY + CCCD are required. Update experimental firmware and retry."))
                        return@post
                    }
                    transfer = V2MetadataTransfer()
                }
                if (!v2 && callbackGatt.getService(SERVICE_UUID) == null) {
                    endSession(ConnectionState.Error("PhysicalLink service not found"))
                    return@post
                }
                // Remain Discovering until metadata, initial reads, and subscriptions succeed.
                // Each target is used once per operation type in this session.
                // Android callbacks carry no request ID; never retry a timed-out
                // request on the same GATT, where a late callback is ambiguous.
                val deadline = Runnable { if (gatt === callbackGatt && _connectionState.value == ConnectionState.Discovering) endSession(ConnectionState.Error("Metadata setup timed out. Check firmware poll() and retry.")) }
                setupTimeout = deadline
                handler.postDelayed(deadline, 120_000)
                operationQueue.enqueue(
                    listOf(GattOperation.ReadCharacteristic(if (v2) V2Protocol.METADATA else CAPABILITIES_UUID))
                )
            }
        }

        override fun onCharacteristicWrite(callbackGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            handler.post {
                if (callbackGatt !== gatt || characteristic !== activeCharacteristic) return@post
                val operation = operationQueue.current as? GattOperation.WriteControl ?: return@post
                if (characteristic.uuid != V2Protocol.CONTROL) return@post
                if (status == BluetoothGatt.GATT_SUCCESS) operationQueue.enqueue(listOf(GattOperation.ReadCharacteristic(if (operation.value.firstOrNull() == 2.toByte()) V2Protocol.STATE else V2Protocol.METADATA)))
                completeOperation(operation, status)
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
                if (v2 && operation.characteristicUuid == V2Protocol.STATE && status == BluetoothGatt.GATT_SUCCESS) startCapture()
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
                if (v2 && characteristic.uuid == V2Protocol.STATE) {
                    try {
                        val session = checkNotNull(live)
                        require(android.os.SystemClock.elapsedRealtime() < commandDeadline) { "Capture deadline exceeded; check firmware poll()" }
                        if (!session.acceptImage(value, android.os.SystemClock.elapsedRealtime())) {
                            completeOperation(operation, status)
                            handler.postDelayed({
                                if (gatt === callbackGatt && live === session) operationQueue.enqueue(listOf(GattOperation.ReadCharacteristic(V2Protocol.STATE)))
                            }, 100)
                            return@post
                        }
                        publishV2(session.state)
                        setupTimeout?.let(handler::removeCallbacks)
                        setupTimeout = null
                        _connectionState.value = ConnectionState.Connected
                        scheduleRecovery(callbackGatt, session)
                    } catch (e: Exception) {
                        endSession(ConnectionState.Error("V2 synchronization failed: ${e.message}. Reconnect to retry."))
                        return@post
                    }
                } else if (v2 && characteristic.uuid == V2Protocol.METADATA) {
                    try {
                        val current = checkNotNull(transfer)
                        if (current.nextPage > 0) require(android.os.SystemClock.elapsedRealtime() < commandDeadline) { "Page selection exceeded five-second deadline; check firmware poll()" }
                        val accepted = current.accept(value)
                        if (!accepted) {
                            require(android.os.SystemClock.elapsedRealtime() < commandDeadline) { "Page selection timed out; check firmware poll()" }
                            completeOperation(operation, status)
                            handler.postDelayed({
                                if (gatt === callbackGatt && transfer === current) operationQueue.enqueue(listOf(GattOperation.ReadCharacteristic(V2Protocol.METADATA)))
                            }, 100)
                            return@post
                        }
                        if (current.complete) {
                            val manifest = current.result()
                            _v2Metadata.value = manifest
                            _dashboardDeviceId.value = manifest.dashboardDeviceId()
                            _capabilities.value = manifest.dashboardCapabilities()
                            onSessionStarted(manifest.dashboardDeviceId())
                            live = V2LiveSession(manifest)
                            operationQueue.enqueue(listOf(GattOperation.EnableNotifications(V2Protocol.STATE)))
                        } else operationQueue.enqueue(listOf(GattOperation.WriteControl(current.command())))
                    } catch (e: Exception) {
                        endSession(ConnectionState.Error("Invalid v2 metadata: ${e.message}. Check experimental firmware and retry."))
                        return@post
                    }
                } else if (characteristic.uuid == CAPABILITIES_UUID) {
                    try {
                        require(value.size <= DeviceCapabilitiesParser.MAX_JSON_LENGTH) { "Capability payload is too large" }
                        val parsed = DeviceCapabilitiesParser.parse(value.decodeToString(throwOnInvalidSequence = true))
                        val sensors = parsed.capabilities.associateBy { capabilityUuid(it.uuid) }
                        val service = checkNotNull(callbackGatt.getService(SERVICE_UUID)) { "PhysicalLink service not found" }
                        val operations = sensors.keys.flatMap { sensorUuid ->
                            val sensor = checkNotNull(service.getCharacteristic(sensorUuid)) {
                                "Sensor characteristic $sensorUuid not found"
                            }
                            val properties = sensor.properties
                            val readable = properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
                            val notifiable = properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                                BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                            check(readable || notifiable) { "Sensor characteristic $sensorUuid cannot be read or subscribed" }
                            buildList {
                                if (readable) add(GattOperation.ReadCharacteristic(sensorUuid))
                                if (notifiable) {
                                    checkNotNull(sensor.getDescriptor(CCCD_UUID)) { "Notification descriptor missing for $sensorUuid" }
                                    add(GattOperation.EnableNotifications(sensorUuid))
                                }
                            }
                        }
                        // Validate all metadata and GATT targets before publishing to dashboard persistence.
                        sensorCapabilitiesByUuid = sensors
                        pendingCapabilities = parsed
                        operationQueue.enqueue(operations)
                    } catch (e: Exception) {
                        Log.e("PhysicalLink", "Invalid capability description", e)
                        endSession(
                            ConnectionState.Error("Invalid capability description: ${e.message ?: "unsupported metadata"}. Check device firmware and reconnect.")
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
            if (callbackGatt !== gatt) return@post
            if (v2) {
                if (characteristic !== callbackGatt.getService(V2Protocol.SERVICE)?.getCharacteristic(V2Protocol.STATE)) return@post
                try {
                    val session = live ?: return@post
                    session.notification(value, android.os.SystemClock.elapsedRealtime())
                    publishV2(session.state)
                } catch (e: Exception) {
                    endSession(ConnectionState.Error("Invalid v2 live record: ${e.message}. Reconnect to retry."))
                }
                return@post
            }
            if (characteristic.service?.uuid != SERVICE_UUID) return@post
            updateReading(characteristic.uuid, value)
        }
    }

    private fun publishV2(state: com.dacodahub.physicallink.domain.model.V2LiveState) {
        _v2Live.value = state
        val capabilities = _capabilities.value ?: return
        dashboardUpdates.accept(state) { id, reading ->
            val capability = capabilities.capabilities.firstOrNull { it.id == id } ?: return@accept
            val raw = reading.value?.toString().orEmpty()
            _sensorValues.value = _sensorValues.value + (id to raw)
            onV2Reading(capability, raw, reading.receivedAtMs - (if (reading.value != null) reading.ageMs else 0))
        }
    }

    private fun startCapture() {
        try {
            val session = checkNotNull(live)
            val command = session.beginCapture()
            lastCaptureAt = android.os.SystemClock.elapsedRealtime()
            publishV2(session.state)
            operationQueue.enqueue(listOf(GattOperation.WriteControl(command)))
        } catch (e: Exception) {
            endSession(ConnectionState.Error("V2 capture failed: ${e.message}. Reconnect to retry."))
        }
    }

    private fun scheduleRecovery(client: BluetoothGatt, session: V2LiveSession) {
        recoveryTick?.let(handler::removeCallbacks)
        val tick = object : Runnable {
            override fun run() {
                if (gatt !== client || live !== session) return
                val elapsed = android.os.SystemClock.elapsedRealtime() - lastCaptureAt
                // Coalesce gaps and prevent command storms. Periodic capture also recovers silent loss.
                if (!session.state.capturing && operationQueue.current == null &&
                    (elapsed >= 30_000 || (session.gap && elapsed >= 5_000))) startCapture()
                if (gatt === client && live === session) handler.postDelayed(this, 1_000)
            }
        }
        recoveryTick = tick
        handler.postDelayed(tick, 1_000)
    }

    private fun updateReading(uuid: UUID, value: ByteArray) {
        val text = value.toString(Charsets.UTF_8)

        val capability = sensorCapabilitiesByUuid[uuid]

        if (capability != null) {
            onSensorReading(capability, text)
            _sensorValues.value += (capability.id to text)
        }

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
            if (disconnectingGatt != null) return@onMain // Repeated taps must not shorten graceful teardown.
            endSession(ConnectionState.Disconnected)
            return@onMain
        }

        cancelTimeout()
        setupTimeout?.let(handler::removeCallbacks)
        setupTimeout = null
        transfer = null
        recoveryTick?.let(handler::removeCallbacks)
        recoveryTick = null
        live = null
        _v2Live.value = com.dacodahub.physicallink.domain.model.V2LiveState()
        v2 = false
        _v2Metadata.value = null
        operationQueue.clear()
        activeCharacteristic = null
        activeDescriptor = null

        gatt = null
        linkConnected = false
        disconnectingGatt = oldGatt

        _readings.value = SensorReadings()
        onSessionEnded()
        _sensorValues.value = emptyMap()
        pendingCapabilities = null
        sensorCapabilitiesByUuid = emptyMap()
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

        } catch (e: RuntimeException) {
            Log.w("PhysicalLink", "Cannot disconnect", e)
            finishDisconnect(oldGatt)
        }
    }

    private fun finishDisconnect(oldGatt: BluetoothGatt) {
        if (disconnectingGatt !== oldGatt) return

        disconnectTimeout?.let(handler::removeCallbacks)
        disconnectTimeout = null
        disconnectingGatt = null

        closeGatt(oldGatt)
    }

    private fun closeGatt(client: BluetoothGatt) {
        try { client.close() }
        catch (error: RuntimeException) { Log.w("PhysicalLink", "Cannot close GATT", error) }
    }

    /** Recheck on adapter broadcasts and foreground entry; no automatic reconnection. */
    fun refreshEnvironment() = onMain {
        if (gatt == null && disconnectingGatt == null) return@onMain
        try {
            if (Build.VERSION.SDK_INT >= 31 &&
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("Bluetooth permission revoked")
            }
            val adapter = bluetoothManager?.adapter
            if (adapter == null) endSession(ConnectionState.Error("Bluetooth unavailable on this device."))
            else if (!adapter.isEnabled) {
                endSession(ConnectionState.Error("Bluetooth is disabled. Turn it on, then reconnect."))
            }
        } catch (_: SecurityException) {
            endSession(ConnectionState.Error("Bluetooth permission denied. Allow Nearby devices in app settings, then reconnect."))
        }
    }

    private fun endSession(state: ConnectionState) {
        Log.d("PhysicalLink", "endSession called: $state")
        cancelTimeout()
        setupTimeout?.let(handler::removeCallbacks)
        setupTimeout = null
        transfer = null
        recoveryTick?.let(handler::removeCallbacks)
        recoveryTick = null
        live = null
        _v2Live.value = com.dacodahub.physicallink.domain.model.V2LiveState()
        v2 = false
        _v2Metadata.value = null
        operationQueue.clear()
        activeCharacteristic = null
        activeDescriptor = null
        linkConnected = false
        val oldGatt = gatt
        gatt = null // Invalidate before disconnect/close can deliver callbacks.
        disconnectingGatt?.let(::finishDisconnect)
        if (oldGatt != null) {
            try { oldGatt.disconnect() }
            catch (error: RuntimeException) { Log.w("PhysicalLink", "Cannot disconnect GATT", error) }
            finally { closeGatt(oldGatt) }
        }

        _readings.value = SensorReadings()
        onSessionEnded()
        _sensorValues.value = emptyMap()
        pendingCapabilities = null
        sensorCapabilitiesByUuid = emptyMap()
        _capabilities.value = null
        _connectionState.value = state
    }
}
