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
    @Test fun `receive observer captures identical packets and rejects retired session callbacks`() {
        val readings = mutableListOf<String>()
        val starts = mutableListOf<String>()
        var ends = 0
        manager = BleConnectionManager(context, { capability, value ->
            assertEquals("soil", capability.id); readings += value
        }, { starts += it }, { ends++ })
        services(); readMetadata()
        callback.onCharacteristicRead(client, sensor, "12".toByteArray(), 0); idle()
        callback.onCharacteristicChanged(client, sensor, "12".toByteArray()); idle()
        assertEquals(listOf("12", "12"), readings)
        assertEquals(listOf("AA:BB:CC:DD:EE:FF"), starts)
        val previous = callback
        manager.disconnect(); idle()
        val before = ends
        previous.onCharacteristicChanged(client, sensor, "99".toByteArray()); idle()
        assertEquals(2, readings.size)
        assertTrue(before > 0)
        nextClient = mock(BluetoothGatt::class.java)
        manager.connect("11:22:33:44:55:66"); idle()
        previous.onCharacteristicChanged(client, sensor, "100".toByteArray()); idle()
        previous.onConnectionStateChange(client, 8, BluetoothProfile.STATE_DISCONNECTED); idle()
        assertEquals(2, readings.size)
        assertEquals("11:22:33:44:55:66", starts.last())
        assertEquals(ConnectionState.Connecting, manager.connectionState.value)
    }

    @Test fun `connected and capabilities are withheld until every setup operation succeeds`() {
        services()
        assertEquals(ConnectionState.Discovering, manager.connectionState.value)
        readMetadata()
        assertNull(manager.capabilities.value)
        callback.onCharacteristicRead(client, sensor, "12".toByteArray(), 0); idle()
        assertEquals(ConnectionState.Discovering, manager.connectionState.value)
        callback.onDescriptorWrite(client, sensor.getDescriptor(uuid("2902")), 0); idle()
        assertEquals(ConnectionState.Connected, manager.connectionState.value)
        assertEquals("soil", manager.capabilities.value!!.capabilities.single().id)
    }
    @Test fun `zero status disconnect before link completion is a failure not user cancellation`() {
        connect()
        callback.onConnectionStateChange(client, 0, BluetoothProfile.STATE_DISCONNECTED); idle()
        assertTrue(manager.connectionState.value is ConnectionState.Error)
        verify(client).close()
    }

    @Test fun `failed subscription cannot publish capabilities to dashboard reconciliation`() {
        val published = mutableListOf<com.dacodahub.physicallink.domain.model.DeviceCapabilities?>()
        val observer = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).launch {
            manager.capabilities.collect { published += it }
        }
        services(); readMetadata()
        callback.onCharacteristicRead(client, sensor, "12".toByteArray(), 0); idle()
        callback.onDescriptorWrite(client, sensor.getDescriptor(uuid("2902")), BluetoothGatt.GATT_FAILURE); idle()
        assertTrue(published.all { it == null })
        assertTrue(manager.connectionState.value is ConnectionState.Error)
        verify(client).close()
        observer.cancel()
    }
    @Test fun `duplicate service callback cannot enqueue reads twice or cancel setup timeout`() {
        services()
        callback.onServicesDiscovered(client, 0); idle()
        verify(client, times(1)).readCharacteristic(metadata)
        idleFor(6)
        verify(client).close()
        assertTrue(manager.connectionState.value is ConnectionState.Error)
    }
    @Test fun `same address retry ignores old reads descriptors disconnects and timeout`() {
        services(); readMetadata()
        manager.disconnect(); idle()
        val oldCallback = callback
        val replacement = mock(BluetoothGatt::class.java)
        nextClient = replacement
        manager.connect("AA:BB:CC:DD:EE:FF"); idle()
        oldCallback.onCharacteristicRead(client, sensor, "99".toByteArray(), 0)
        oldCallback.onDescriptorWrite(client, sensor.getDescriptor(uuid("2902")), 0)
        oldCallback.onServicesDiscovered(client, 0)
        oldCallback.onConnectionStateChange(client, 0, BluetoothProfile.STATE_DISCONNECTED)
        idleFor(6)
        assertEquals(ConnectionState.Connecting, manager.connectionState.value)
        assertTrue(manager.sensorValues.value.isEmpty())
        assertNull(manager.capabilities.value)
        verify(client).close()
        verify(replacement, never()).close()
    }
    @Test fun `cancelling metadata read clears queued setup and rejects every late callback`() {
        services()
        manager.disconnect(); idle()
        readMetadata()
        callback.onCharacteristicChanged(client, sensor, "99".toByteArray()); idle()
        idleFor(6)
        assertEquals(ConnectionState.Disconnected, manager.connectionState.value)
        assertNull(manager.capabilities.value)
        assertTrue(manager.sensorValues.value.isEmpty())
        verify(client, never()).readCharacteristic(sensor)
        verify(client).close()
    }
    @Test fun `malformed UTF8 metadata closes the client without publishing capabilities`() {
        services()
        callback.onCharacteristicRead(client, metadata, byteArrayOf(0xc3.toByte(), 0x28), 0); idle()
        assertTrue(manager.connectionState.value is ConnectionState.Error)
        assertNull(manager.capabilities.value)
        verify(client).close()
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

    private fun v2Service(): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic> {
        val svc = BluetoothGattService(V2Protocol.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val meta = BluetoothGattCharacteristic(V2Protocol.METADATA, BluetoothGattCharacteristic.PROPERTY_READ, 0)
        val control = BluetoothGattCharacteristic(V2Protocol.CONTROL, BluetoothGattCharacteristic.PROPERTY_WRITE, 0)
        svc.addCharacteristic(meta); svc.addCharacteristic(control)
        val state = BluetoothGattCharacteristic(V2Protocol.STATE, BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0)
        state.addDescriptor(BluetoothGattDescriptor(uuid("2902"), 0))
        svc.addCharacteristic(state)
        `when`(client.writeDescriptor(any(BluetoothGattDescriptor::class.java), any(ByteArray::class.java))).thenReturn(0)
        `when`(client.getService(V2Protocol.SERVICE)).thenReturn(svc)
        `when`(client.writeCharacteristic(eq(control), any(ByteArray::class.java), eq(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT))).thenReturn(0)
        return meta to control
    }
    private fun v2Page(key: String): ByteArray {
        val fixture = org.json.JSONObject(java.io.File("../firmware/soil-monitor/test/protocol_v2/fixtures/numeric-v2.json").readText())
        return fixture.getJSONObject("wire").getString(key).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    @Test fun `v2 takes precedence and only publishes complete metadata`() {
        val (meta, control) = v2Service(); services()
        verify(client).readCharacteristic(meta)
        verify(client, never()).readCharacteristic(metadata)
        callback.onCharacteristicRead(client, meta, v2Page("page.sixteen.0"), 0); idle()
        assertNull(manager.v2Metadata.value)
        verify(client).writeCharacteristic(eq(control), eq(byteArrayOf(1, 1)), eq(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT))
        callback.onCharacteristicWrite(client, control, 0); idle()
        callback.onCharacteristicRead(client, meta, v2Page("page.sixteen.0"), 0); idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        callback.onCharacteristicRead(client, meta, v2Page("page.sixteen.1"), 0); idle()
        assertEquals(ConnectionState.Discovering, manager.connectionState.value)
        assertEquals(16, manager.v2Metadata.value?.sensors?.size)
        assertEquals(16, manager.capabilities.value?.capabilities?.size)
        assertEquals("v2:${manager.v2Metadata.value!!.deviceId}", manager.dashboardDeviceId.value)
        assertTrue(manager.sensorValues.value.isEmpty())
    }
    @Test fun `invalid v2 never downgrades to v1`() {
        `when`(client.getService(V2Protocol.SERVICE)).thenReturn(BluetoothGattService(V2Protocol.SERVICE, 0))
        services(); assertTrue(manager.connectionState.value is ConnectionState.Error)
        verify(client, never()).readCharacteristic(metadata)
    }
    @Test fun `disconnect during v2 setup invalidates late read and write`() {
        val (meta, control) = v2Service(); services()
        callback.onCharacteristicRead(client, meta, v2Page("page.sixteen.0"), 0); idle()
        manager.disconnect(); idle()
        callback.onCharacteristicWrite(client, control, 0)
        callback.onCharacteristicRead(client, meta, v2Page("page.sixteen.1"), 0); idle()
        assertNull(manager.v2Metadata.value)
        assertEquals(ConnectionState.Disconnected, manager.connectionState.value)
    }

    @Test fun `v2 selection read deadline is bounded`() {
        val (meta, control) = v2Service(); services()
        callback.onCharacteristicRead(client, meta, v2Page("page.sixteen.0"), 0); idle()
        callback.onCharacteristicWrite(client, control, 0); idle()
        idleFor(6)
        assertTrue(manager.connectionState.value is ConnectionState.Error)
        assertNull(manager.v2Metadata.value)
    }
    @Test fun `v2 stale callback cannot publish into newer session`() {
        val (meta, _) = v2Service(); services()
        manager.disconnect(); idle()
        val replacement = mock(BluetoothGatt::class.java)
        nextClient = replacement
        connect()
        callback.onCharacteristicRead(client, meta, v2Page("page.one.0"), 0); idle()
        assertEquals(ConnectionState.Connecting, manager.connectionState.value)
        assertNull(manager.v2Metadata.value)
    }

    private fun stateCharacteristic() = client.getService(V2Protocol.SERVICE).getCharacteristic(V2Protocol.STATE)
    private fun subscribeV2(): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic> {
        val pair = v2Service(); services()
        callback.onCharacteristicRead(client, pair.first, v2Page("page.one.0"), 0); idle()
        assertFalse(manager.v2Live.value.synchronized)
        verify(client, never()).writeCharacteristic(eq(pair.second), eq(byteArrayOf(2, 1, 0)), eq(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT))
        callback.onDescriptorWrite(client, stateCharacteristic().getDescriptor(uuid("2902")), 0); idle()
        verify(client).writeCharacteristic(eq(pair.second), eq(byteArrayOf(2, 1, 0)), eq(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT))
        callback.onCharacteristicWrite(client, pair.second, 0); idle()
        return pair
    }
    private fun synchronizeV2(): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic> {
        val pair = subscribeV2()
        callback.onCharacteristicRead(client, stateCharacteristic(), v2Page("image.1"), 0); idle()
        assertTrue(manager.v2Live.value.synchronized)
        assertEquals(ConnectionState.Connected, manager.connectionState.value)
        return pair
    }
    @Test fun `v2 capture waits for descriptor then polls delayed image and merges live`() {
        subscribeV2()
        callback.onCharacteristicRead(client, stateCharacteristic(), v2Page("image.initial"), 0); idle()
        assertFalse(manager.v2Live.value.synchronized)
        callback.onCharacteristicChanged(client, stateCharacteristic(), v2Page("record.valid")); idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        callback.onCharacteristicRead(client, stateCharacteristic(), v2Page("image.1"), 0); idle()
        assertEquals(42L, manager.v2Live.value.readings.values.single().sequence)
        assertEquals(manager.v2Live.value.readings.values.single().value.toString(), manager.sensorValues.value.values.single())
        assertEquals(2, manager.capabilities.value?.version)
    }
    @Test fun `v2 periodic recovery captures missing notifications and resets on reconnect`() {
        val (_, control) = synchronizeV2()
        idleFor(30)
        verify(client).writeCharacteristic(eq(control), eq(byteArrayOf(2, 2, 0)), eq(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT))
        callback.onCharacteristicWrite(client, control, 0); idle()
        val image = v2Page("image.1").also { it[2] = 2; it[20] = 7 }
        callback.onCharacteristicRead(client, stateCharacteristic(), image, 0); idle()
        assertEquals(7L, manager.v2Live.value.readings.values.single().sequence)
        manager.disconnect(); idle(); assertTrue(manager.v2Live.value.readings.isEmpty())
        clearInvocations(client)
        synchronizeV2()
        assertEquals(1, manager.v2Live.value.requestId)
    }
    @Test fun `v2 gaps coalesce into bounded recovery`() {
        val (_, control) = synchronizeV2()
        callback.onCharacteristicChanged(client, stateCharacteristic(), v2Page("record.valid")); idle()
        idleFor(4)
        verify(client, never()).writeCharacteristic(eq(control), eq(byteArrayOf(2, 2, 0)), eq(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT))
        idleFor(1)
        verify(client).writeCharacteristic(eq(control), eq(byteArrayOf(2, 2, 0)), eq(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT))
        idleFor(6)
        assertTrue(manager.connectionState.value is ConnectionState.Error)
        assertTrue(manager.v2Live.value.readings.isEmpty())
    }
    @Test fun `v2 state epoch failure and late notification cannot change replacement session`() {
        subscribeV2()
        val state = stateCharacteristic()
        callback.onCharacteristicRead(client, state, v2Page("image.1").also { it[4] = 0 }, 0); idle()
        assertTrue(manager.connectionState.value is ConnectionState.Error)
        assertTrue(manager.v2Live.value.readings.isEmpty())
        nextClient = mock(BluetoothGatt::class.java); connect()
        callback.onCharacteristicChanged(client, state, v2Page("record.valid")); idle()
        callback.onCharacteristicRead(client, state, v2Page("image.1"), 0); idle()
        assertEquals(ConnectionState.Connecting, manager.connectionState.value)
        assertTrue(manager.v2Live.value.readings.isEmpty())
    }
    @Test fun `v2 missing CCCD is rejected without downgrade`() {
        v2Service()
        val svc = client.getService(V2Protocol.SERVICE)
        val replacement = BluetoothGattService(V2Protocol.SERVICE, 0)
        replacement.addCharacteristic(svc.getCharacteristic(V2Protocol.METADATA))
        replacement.addCharacteristic(svc.getCharacteristic(V2Protocol.CONTROL))
        replacement.addCharacteristic(BluetoothGattCharacteristic(V2Protocol.STATE, BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0))
        `when`(client.getService(V2Protocol.SERVICE)).thenReturn(replacement)
        services(); assertTrue(manager.connectionState.value is ConnectionState.Error)
    }
}
