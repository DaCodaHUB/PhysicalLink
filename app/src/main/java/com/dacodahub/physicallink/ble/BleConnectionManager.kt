
package com.dacodahub.physicallink.ble

import android.Manifest
import android.R.attr.text
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
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
class BleConnectionManager(
    private val context: Context
) {
    companion object {
        private val SERVICE_UUID =
            UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")

        private val SOIL_UUID =
            UUID.fromString("00005678-0000-1000-8000-00805f9b34fb")

        private val TEMP_UUID =
            UUID.fromString("00005679-0000-1000-8000-00805f9b34fb")

        private val HUMIDITY_UUID =
            UUID.fromString("0000567a-0000-1000-8000-00805f9b34fb")

        private val PRESSURE_UUID =
            UUID.fromString("0000567b-0000-1000-8000-00805f9b34fb")

        private val CCCD_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager =
        context.getSystemService(BluetoothManager::class.java)

    private var gatt: BluetoothGatt? = null

    private val _connectionState =
        MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    val connectionState = _connectionState.asStateFlow()

    private val _readings = MutableStateFlow(SensorReadings())
    val readings = _readings.asStateFlow()

    private val readQueue = ArrayDeque<UUID>()

    private val notificationQueue = ArrayDeque<UUID>()

    fun connect(address: String) {
        disconnect()

        val device = bluetoothManager.adapter
            .getRemoteDevice(address)

        _connectionState.value = ConnectionState.Connecting

        gatt = device.connectGatt(
            context,
            false,
            callback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(
            callbackGatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            Log.d(
                "PhysicalLink",
                "GATT state: status=$status, newState=$newState"
            )

            // Ignore callbacks from an old connection.
            if (callbackGatt !== gatt) {
                callbackGatt.close()
                return
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED ||
                status != BluetoothGatt.GATT_SUCCESS
            ) {
                val wasConnected =
                    _connectionState.value is ConnectionState.Connected

                readQueue.clear()
                notificationQueue.clear()

                gatt = null
                callbackGatt.close()

                _readings.value = SensorReadings()

                _connectionState.value = when {
                    wasConnected -> ConnectionState.ConnectionLost
                    status != BluetoothGatt.GATT_SUCCESS ->
                        ConnectionState.Error("Connection failed: $status")
                    else -> ConnectionState.Disconnected
                }

                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = ConnectionState.Discovering

                if (!callbackGatt.discoverServices()) {
                    _connectionState.value =
                        ConnectionState.Error("Could not start discovery")
                }
            }
        }

        override fun onServicesDiscovered(
            gatt: BluetoothGatt,
            status: Int
        ) {
            Log.d(
                "PhysicalLink",
                "Services discovered: status=$status"
            )

            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value =
                    ConnectionState.Error("Service discovery failed: $status")
                return
            }

            val service = gatt.getService(SERVICE_UUID)

            if (service == null) {
                _connectionState.value =
                    ConnectionState.Error("PhysicalLink service not found")
                return
            }

            _connectionState.value = ConnectionState.Connected

            readQueue.clear()
            readQueue.addAll(
                listOf(
                    SOIL_UUID,
                    TEMP_UUID,
                    HUMIDITY_UUID,
                    PRESSURE_UUID
                )
            )

            notificationQueue.clear()
            notificationQueue.addAll(
                listOf(
                    SOIL_UUID,
                    TEMP_UUID,
                    HUMIDITY_UUID,
                    PRESSURE_UUID
                )
            )

            Log.d(
                "PhysicalLink",
                "Queues initialized: reads=${readQueue.size}, notifications=${notificationQueue.size}"
            )

            readNext(gatt)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            Log.d(
                "PhysicalLink",
                "Read completed: ${characteristic.uuid}, status=$status"
            )

            if (status == BluetoothGatt.GATT_SUCCESS) {
                val text = value.toString(Charsets.UTF_8)

                _readings.value = when (characteristic.uuid) {
                    SOIL_UUID ->
                        _readings.value.copy(soil = text)
                    TEMP_UUID ->
                        _readings.value.copy(temperature = text)
                    HUMIDITY_UUID ->
                        _readings.value.copy(humidity = text)
                    PRESSURE_UUID ->
                        _readings.value.copy(pressure = text)
                    else -> _readings.value
                }
            }

            readNext(gatt)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.d("PhysicalLink", "Legacy read callback: status=$status")

            if (status == BluetoothGatt.GATT_SUCCESS) {
                updateReading(characteristic.uuid, characteristic.value)
            }

            readNext(gatt)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            Log.d(
                "PhysicalLink",
                "NOTIFICATION ${characteristic.uuid}: $text"
            )

            updateReading(characteristic.uuid, value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            updateReading(characteristic.uuid, characteristic.value)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d(
                    "PhysicalLink",
                    "CCCD write: ${descriptor.characteristic.uuid}, status=$status"
                )
            }

            subscribeNext(gatt)
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
    }

    private fun readNext(gatt: BluetoothGatt) {
        val service = gatt.getService(SERVICE_UUID) ?: run {
            Log.e("PhysicalLink", "Service missing during read")
            return
        }

        Log.d("PhysicalLink", "readNext: remaining=${readQueue.size}")

        while (readQueue.isNotEmpty()) {
            val uuid = readQueue.removeFirst()
            val characteristic = service.getCharacteristic(uuid) ?: continue

            if (gatt.readCharacteristic(characteristic)) {
                return
            }
        }

        subscribeNext(gatt)
    }

    private fun subscribeNext(gatt: BluetoothGatt) {
        val service = gatt.getService(SERVICE_UUID) ?: return

        while (notificationQueue.isNotEmpty()) {
            val uuid = notificationQueue.removeFirst()
            val characteristic = service.getCharacteristic(uuid) ?: continue

            if (!gatt.setCharacteristicNotification(characteristic, true)) {
                continue
            }

            val descriptor = characteristic.getDescriptor(CCCD_UUID) ?: continue

            val started = if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }

            if (started) return
        }
    }

    fun clearConnectionError() {
        if (_connectionState.value is ConnectionState.ConnectionLost ||
            _connectionState.value is ConnectionState.Error
        ) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    fun disconnect() {
        readQueue.clear()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _readings.value = SensorReadings()
        _connectionState.value = ConnectionState.Disconnected
    }
}
