package com.dacodahub.physicallink.data.ble

import android.app.Application
import android.bluetooth.*
import android.content.Context
import android.os.Looper
import com.dacodahub.physicallink.domain.model.ConnectionState
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class BleConnectionManagerTest {
    private val context = mock(Context::class.java)
    private val bluetooth = mock(BluetoothManager::class.java)
    private val adapter = mock(BluetoothAdapter::class.java)
    private val device = mock(BluetoothDevice::class.java)
    private val client = mock(BluetoothGatt::class.java)
    private var nextClient = client
    private lateinit var callback: BluetoothGattCallback
    private lateinit var manager: BleConnectionManager
    private val service = BluetoothGattService(uuid("1234"), BluetoothGattService.SERVICE_TYPE_PRIMARY)
    private val metadata = BluetoothGattCharacteristic(uuid("5670"), BluetoothGattCharacteristic.PROPERTY_READ, 0)
    private val sensor = BluetoothGattCharacteristic(uuid("5678"),
        BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0)
    private val json = """{"version":1,"device":"Board","capabilities":[{"id":"soil","uuid":"5678","unit":"raw","type":"number"}]}"""

    @Before fun setup() {
        `when`(context.getSystemService(BluetoothManager::class.java)).thenReturn(bluetooth)
        `when`(bluetooth.adapter).thenReturn(adapter)
        `when`(adapter.isEnabled).thenReturn(true)
        `when`(adapter.getRemoteDevice(anyString())).thenReturn(device)
        `when`(device.connectGatt(eq(context), eq(false), any(BluetoothGattCallback::class.java), eq(BluetoothDevice.TRANSPORT_LE)))
            .thenAnswer { callback = it.getArgument(2); nextClient }
        service.addCharacteristic(metadata)
        service.addCharacteristic(sensor)
        sensor.addDescriptor(BluetoothGattDescriptor(uuid("2902"), 0))
        `when`(client.getService(uuid("1234"))).thenReturn(service)
        `when`(client.discoverServices()).thenReturn(true)
        `when`(client.readCharacteristic(any(BluetoothGattCharacteristic::class.java))).thenReturn(true)
        `when`(client.setCharacteristicNotification(any(BluetoothGattCharacteristic::class.java), eq(true))).thenReturn(true)
        manager = BleConnectionManager(context)
    }
    @After fun cleanup() { manager.disconnect(); idleFor(6) }
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun idleFor(seconds: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))
    private fun connect() { manager.connect("AA:BB:CC:DD:EE:FF"); idle() }
    private fun discover() {
        connect()
        callback.onConnectionStateChange(client, 0, BluetoothProfile.STATE_CONNECTED); idle()
    }
    private fun services() { discover(); callback.onServicesDiscovered(client, 0); idle() }
    private fun readMetadata(text: String = json) {
        callback.onCharacteristicRead(client, metadata, text.toByteArray(), 0); idle()
    }

    @Test fun `connection failure closes client and ignores late callbacks`() {
        connect()
        callback.onConnectionStateChange(client, 133, BluetoothProfile.STATE_DISCONNECTED); idle()
        verify(client).close()
        callback.onConnectionStateChange(client, 0, BluetoothProfile.STATE_CONNECTED); idle()
        verify(client, never()).discoverServices()
        assertTrue(manager.connectionState.value is ConnectionState.Error)
    }
    @Test fun `connection and discovery timeouts close clients`() {
        connect(); idleFor(31)
        verify(client).close()
        assertTrue(manager.connectionState.value is ConnectionState.Error)
    }
    @Test fun `discovery timeout closes client`() {
        discover(); idleFor(31)
        verify(client).close()
    }
    @Test fun `discovery callback failure closes client`() {
        discover(); callback.onServicesDiscovered(client, 133); idle()
        verify(client).close()
    }
    @Test fun `rejected discovery closes client`() {
        `when`(client.discoverServices()).thenReturn(false)
        discover()
        verify(client).close()
    }
    @Test fun `operation timeout clears queue and closes without accepting late read`() {
        services(); idleFor(6)
        verify(client).close()
        readMetadata()
        assertNull(manager.capabilities.value)
        verify(client, never()).readCharacteristic(sensor)
    }
    @Test fun `explicit disconnect waits for callback and closes exactly once`() {
        connect(); manager.disconnect(); idle()
        verify(client).disconnect()
        verify(client, never()).close()
        callback.onConnectionStateChange(client, 0, BluetoothProfile.STATE_DISCONNECTED); idle()
        idleFor(6)
        verify(client).close()
        assertEquals(ConnectionState.Disconnected, manager.connectionState.value)
    }
    @Test fun `disconnect fallback closes exactly once even after repeated disconnect`() {
        connect(); manager.disconnect(); manager.disconnect(); idleFor(6)
        callback.onConnectionStateChange(client, 0, BluetoothProfile.STATE_DISCONNECTED); idle()
        verify(client).close()
    }
    @Test fun `replacement closes retiring client before allocating another GATT`() {
        connect(); manager.disconnect(); idle()
        val replacement = mock(BluetoothGatt::class.java)
        nextClient = replacement
        manager.connect("11:22:33:44:55:66"); idle()
        verify(client).close()
        callback.onConnectionStateChange(client, 0, BluetoothProfile.STATE_DISCONNECTED); idle()
        assertEquals(ConnectionState.Connecting, manager.connectionState.value)
        verify(replacement, never()).close()
    }
    @Test fun `permission revocation during disconnect cannot escape cleanup`() {
        connect()
        doThrow(SecurityException("revoked")).`when`(client).disconnect()
        doThrow(SecurityException("revoked")).`when`(client).close()
        manager.disconnect(); idleFor(6)
        assertEquals(ConnectionState.Disconnected, manager.connectionState.value)
    }
    @Test fun `connection loss clears values and closes client`() {
        services(); readMetadata()
        callback.onCharacteristicRead(client, sensor, "12".toByteArray(), 0); idle()
        assertEquals("12", manager.sensorValues.value["soil"])
        callback.onConnectionStateChange(client, 8, BluetoothProfile.STATE_DISCONNECTED); idle()
        assertTrue(manager.sensorValues.value.isEmpty())
        assertEquals(ConnectionState.ConnectionLost, manager.connectionState.value)
        verify(client).close()
    }
    @Test fun `invalid metadata is never published`() {
        services(); readMetadata(json.replace("5678", "not-a-uuid"))
        assertNull(manager.capabilities.value)
        verify(client).close()
        assertTrue(manager.connectionState.value is ConnectionState.Error)
    }


    @Test fun `duplicate connect request does not allocate another GATT`() {
        connect(); connect()
        verify(device, times(1)).connectGatt(eq(context), eq(false), any(BluetoothGattCallback::class.java), eq(BluetoothDevice.TRANSPORT_LE))
        verify(client, never()).close()
    }
    @Test fun `empty capability list completes without subscriptions or timeout`() {
        services(); readMetadata("""{"version":1,"device":"Board","capabilities":[]}""")
        assertTrue(manager.capabilities.value!!.capabilities.isEmpty())
        idleFor(6)
        assertEquals(ConnectionState.Connected, manager.connectionState.value)
        verify(client, never()).setCharacteristicNotification(any(BluetoothGattCharacteristic::class.java), anyBoolean())
    }
    @Test fun `missing service closes client with a useful error`() {
        `when`(client.getService(uuid("1234"))).thenReturn(null)
        services()
        verify(client).close()
        assertTrue((manager.connectionState.value as ConnectionState.Error).message.contains("service not found"))
    }
    @Test fun `missing characteristic cannot publish partial metadata`() {
        services(); readMetadata(json.replace("5678", "ffff"))
        assertNull(manager.capabilities.value)
        verify(client).close()
        assertTrue((manager.connectionState.value as ConnectionState.Error).message.contains("not found"))
    }
    @Test fun `missing descriptor fails before metadata publication`() {
        sensor.removeDescriptorForTest()
        services(); readMetadata()
        assertNull(manager.capabilities.value)
        verify(client).close()
    }
    @Test fun `read only sensors are read without trying to subscribe`() {
        val readOnly = BluetoothGattCharacteristic(uuid("5678"), BluetoothGattCharacteristic.PROPERTY_READ, 0)
        replaceSensor(readOnly)
        services(); readMetadata()
        callback.onCharacteristicRead(client, readOnly, "9".toByteArray(), 0); idle()
        verify(client, never()).setCharacteristicNotification(any(BluetoothGattCharacteristic::class.java), anyBoolean())
        assertEquals("9", manager.sensorValues.value["soil"])
        idleFor(6)
        assertEquals(ConnectionState.Connected, manager.connectionState.value)
    }
    @Test fun `notification only sensor does not issue unsupported read`() {
        val notify = BluetoothGattCharacteristic(uuid("5678"), BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0)
        notify.addDescriptor(BluetoothGattDescriptor(uuid("2902"), 0))
        replaceSensor(notify)
        services(); readMetadata()
        verify(client, never()).readCharacteristic(notify)
        verify(client).setCharacteristicNotification(notify, true)
    }
    @Test fun `indication only sensors use the indication CCCD value`() {
        val indication = BluetoothGattCharacteristic(uuid("5678"), BluetoothGattCharacteristic.PROPERTY_INDICATE, 0)
        val descriptor = BluetoothGattDescriptor(uuid("2902"), 0)
        indication.addDescriptor(descriptor)
        replaceSensor(indication)
        services(); readMetadata()
        verify(client).writeDescriptor(eq(descriptor), org.mockito.AdditionalMatchers.aryEq(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE))
    }
    @Test fun `failed read aborts queue and closes`() {
        services()
        callback.onCharacteristicRead(client, metadata, byteArrayOf(), BluetoothGatt.GATT_FAILURE); idle()
        verify(client).close()
        verify(client, never()).readCharacteristic(sensor)
    }
    @Test fun `permission failure starting a queued operation closes safely`() {
        `when`(client.readCharacteristic(metadata)).thenThrow(SecurityException("revoked"))
        services()
        verify(client).close()
        assertTrue((manager.connectionState.value as ConnectionState.Error).message.contains("permission"))
    }
    @Test fun `descriptor failure closes and late descriptor cannot affect next client`() {
        services(); readMetadata()
        callback.onCharacteristicRead(client, sensor, "8".toByteArray(), 0); idle()
        val descriptor = sensor.getDescriptor(uuid("2902"))
        callback.onDescriptorWrite(client, descriptor, BluetoothGatt.GATT_FAILURE); idle()
        verify(client).close()
        nextClient = mock(BluetoothGatt::class.java)
        manager.connect("11:22:33:44:55:66"); idle()
        callback.onDescriptorWrite(client, descriptor, 0)
        callback.onServicesDiscovered(client, 0)
        callback.onCharacteristicChanged(client, sensor, "99".toByteArray()); idle()
        assertEquals(ConnectionState.Connecting, manager.connectionState.value)
        assertTrue(manager.sensorValues.value.isEmpty())
    }
    @Test fun `adapter off and revoked permission invalidate and close idle session`() {
        connect()
        `when`(adapter.isEnabled).thenReturn(false)
        manager.refreshEnvironment(); idle()
        verify(client).close()
        assertTrue(manager.connectionState.value is ConnectionState.Error)
    }
    @Test fun `revoked connect permission closes without crashing on foreground validation`() {
        connect()
        `when`(context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT))
            .thenReturn(android.content.pm.PackageManager.PERMISSION_DENIED)
        manager.refreshEnvironment(); idle()
        verify(client).close()
        assertTrue((manager.connectionState.value as ConnectionState.Error).message.contains("permission"))
    }
    @Test fun `new device identity cannot be observed with previous metadata or readings`() {
        services(); readMetadata()
        callback.onCharacteristicRead(client, sensor, "7".toByteArray(), 0); idle()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val observer = scope.launchForDeviceId()
        nextClient = mock(BluetoothGatt::class.java)
        manager.connect("11:22:33:44:55:66"); idle()
        assertEquals("11:22:33:44:55:66", manager.deviceId.value)
        observer.cancel()
    }
    private fun kotlinx.coroutines.CoroutineScope.launchForDeviceId() = launch {
        manager.deviceId.collect { id ->
            if (id == "11:22:33:44:55:66") {
                assertNull(manager.capabilities.value)
                assertTrue(manager.sensorValues.value.isEmpty())
            }
        }
    }
    private fun replaceSensor(replacement: BluetoothGattCharacteristic) {
        val replacementService = BluetoothGattService(uuid("1234"), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        replacementService.addCharacteristic(metadata)
        replacementService.addCharacteristic(replacement)
        `when`(client.getService(uuid("1234"))).thenReturn(replacementService)
    }
    private fun BluetoothGattCharacteristic.removeDescriptorForTest() {
        replaceSensor(BluetoothGattCharacteristic(uuid("5678"), properties, 0))
    }
    private fun uuid(short: String) = UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")
}
