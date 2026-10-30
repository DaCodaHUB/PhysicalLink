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
    @Before fun setup() {
        `when`(context.getSystemService(BluetoothManager::class.java)).thenReturn(bluetooth)
        `when`(bluetooth.adapter).thenReturn(adapter)
        `when`(adapter.isEnabled).thenReturn(true)
        `when`(adapter.getRemoteDevice(anyString())).thenReturn(device)
        `when`(device.connectGatt(eq(context), eq(false), any(BluetoothGattCallback::class.java), eq(BluetoothDevice.TRANSPORT_LE)))
            .thenAnswer { callback = it.getArgument(2); nextClient }
        `when`(client.discoverServices()).thenReturn(true)
        `when`(client.readCharacteristic(any(BluetoothGattCharacteristic::class.java))).thenReturn(true)
        `when`(client.setCharacteristicNotification(any(BluetoothGattCharacteristic::class.java), eq(true))).thenReturn(true)
        protocolService()
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

    @Test fun `duplicate connect request does not allocate another GATT`() {
        connect(); connect()
        verify(device, times(1)).connectGatt(eq(context), eq(false), any(BluetoothGattCallback::class.java), eq(BluetoothDevice.TRANSPORT_LE))
        verify(client, never()).close()
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
    @Test fun `zero status disconnect before link completion is a failure not user cancellation`() {
        connect()
        callback.onConnectionStateChange(client, 0, BluetoothProfile.STATE_DISCONNECTED); idle()
        assertTrue(manager.connectionState.value is ConnectionState.Error)
        verify(client).close()
    }

    @Test fun `legacy only service is rejected`() {
        `when`(client.getService(PhysicalLinkProtocol.SERVICE)).thenReturn(null)
        services()
        verify(client).close()
        assertTrue(manager.connectionState.value is ConnectionState.Error)
    }
    @Test fun `live connection loss clears values and ends session`() {
        synchronize()
        assertTrue(manager.sensorValues.value.isNotEmpty())
        callback.onConnectionStateChange(client, 8, BluetoothProfile.STATE_DISCONNECTED); idle()
        assertTrue(manager.sensorValues.value.isEmpty())
        assertEquals(ConnectionState.ConnectionLost, manager.connectionState.value)
        verify(client).close()
    }
    @Test fun `metadata timeout ignores late read`() {
        val (meta, _) = protocolService(); services(); idleFor(6)
        callback.onCharacteristicRead(client, meta, fixtureBytes("page.one.0"), 0); idle()
        assertNull(manager.manifest.value)
        verify(client).close()
    }

    private fun uuid(short: String) = UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")

    private fun protocolService(): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic> {
        val svc = BluetoothGattService(PhysicalLinkProtocol.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val meta = BluetoothGattCharacteristic(PhysicalLinkProtocol.METADATA, BluetoothGattCharacteristic.PROPERTY_READ, 0)
        val control = BluetoothGattCharacteristic(PhysicalLinkProtocol.CONTROL, BluetoothGattCharacteristic.PROPERTY_WRITE, 0)
        svc.addCharacteristic(meta); svc.addCharacteristic(control)
        val state = BluetoothGattCharacteristic(PhysicalLinkProtocol.STATE, BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0)
        state.addDescriptor(BluetoothGattDescriptor(uuid("2902"), 0))
        svc.addCharacteristic(state)
        `when`(client.writeDescriptor(any(BluetoothGattDescriptor::class.java), any(ByteArray::class.java))).thenReturn(0)
        `when`(client.getService(PhysicalLinkProtocol.SERVICE)).thenReturn(svc)
        `when`(client.writeCharacteristic(eq(control), any(ByteArray::class.java), eq(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT))).thenReturn(0)
        return meta to control
    }
    private fun fixtureBytes(key: String): ByteArray {
        val fixture = org.json.JSONObject(java.io.File("../firmware/soil-monitor/test/protocol_v2/fixtures/numeric-v2.json").readText())
        return fixture.getJSONObject("wire").getString(key).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    @Test fun `only complete metadata is published`() {
        val (meta, control) = protocolService(); services()
        verify(client).readCharacteristic(meta)
        callback.onCharacteristicRead(client, meta, fixtureBytes("page.sixteen.0"), 0); idle()
        assertNull(manager.manifest.value)
        verify(client).writeCharacteristic(eq(control), eq(byteArrayOf(1, 1)), eq(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT))
        callback.onCharacteristicWrite(client, control, 0); idle()
        callback.onCharacteristicRead(client, meta, fixtureBytes("page.sixteen.0"), 0); idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        callback.onCharacteristicRead(client, meta, fixtureBytes("page.sixteen.1"), 0); idle()
        assertEquals(ConnectionState.Discovering, manager.connectionState.value)
        assertEquals(16, manager.manifest.value?.sensors?.size)
        assertEquals(16, manager.capabilities.value?.capabilities?.size)
        assertEquals("v2:${manager.manifest.value!!.deviceId}", manager.dashboardDeviceId.value)
        assertTrue(manager.sensorValues.value.isEmpty())
    }

    @Test fun `selection read deadline is bounded`() {
        val (meta, control) = protocolService(); services()
        callback.onCharacteristicRead(client, meta, fixtureBytes("page.sixteen.0"), 0); idle()
        callback.onCharacteristicWrite(client, control, 0); idle()
        idleFor(6)
        assertTrue(manager.connectionState.value is ConnectionState.Error)
        assertNull(manager.manifest.value)
    }
    @Test fun `stale callback cannot publish into newer session`() {
        val (meta, _) = protocolService(); services()
        manager.disconnect(); idle()
        val replacement = mock(BluetoothGatt::class.java)
        nextClient = replacement
        connect()
        callback.onCharacteristicRead(client, meta, fixtureBytes("page.one.0"), 0); idle()
        assertEquals(ConnectionState.Connecting, manager.connectionState.value)
        assertNull(manager.manifest.value)
    }

    private fun stateCharacteristic() = client.getService(PhysicalLinkProtocol.SERVICE).getCharacteristic(PhysicalLinkProtocol.STATE)
    private fun subscribe(): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic> {
        val pair = protocolService(); services()
        callback.onCharacteristicRead(client, pair.first, fixtureBytes("page.one.0"), 0); idle()
        assertFalse(manager.liveState.value.synchronized)
        verify(client, never()).writeCharacteristic(eq(pair.second), eq(byteArrayOf(2, 1, 0)), eq(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT))
        callback.onDescriptorWrite(client, stateCharacteristic().getDescriptor(uuid("2902")), 0); idle()
        verify(client).writeCharacteristic(eq(pair.second), eq(byteArrayOf(2, 1, 0)), eq(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT))
        callback.onCharacteristicWrite(client, pair.second, 0); idle()
        return pair
    }
    private fun synchronize(): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic> {
        val pair = subscribe()
        callback.onCharacteristicRead(client, stateCharacteristic(), fixtureBytes("image.1"), 0); idle()
        assertTrue(manager.liveState.value.synchronized)
        assertEquals(ConnectionState.Connected, manager.connectionState.value)
        return pair
    }
    @Test fun `capture waits for descriptor then polls delayed image and merges live`() {
        subscribe()
        callback.onCharacteristicRead(client, stateCharacteristic(), fixtureBytes("image.initial"), 0); idle()
        assertFalse(manager.liveState.value.synchronized)
        callback.onCharacteristicChanged(client, stateCharacteristic(), fixtureBytes("record.valid")); idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        callback.onCharacteristicRead(client, stateCharacteristic(), fixtureBytes("image.1"), 0); idle()
        assertEquals(42L, manager.liveState.value.readings.values.single().sequence)
        assertEquals(manager.liveState.value.readings.values.single().value.toString(), manager.sensorValues.value.values.single())
        assertEquals(2, manager.capabilities.value?.version)
    }
    @Test fun `periodic recovery captures missing notifications and resets on reconnect`() {
        val (_, control) = synchronize()
        idleFor(30)
        verify(client).writeCharacteristic(eq(control), eq(byteArrayOf(2, 2, 0)), eq(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT))
        callback.onCharacteristicWrite(client, control, 0); idle()
        val image = fixtureBytes("image.1").also { it[2] = 2; it[20] = 7 }
        callback.onCharacteristicRead(client, stateCharacteristic(), image, 0); idle()
        assertEquals(7L, manager.liveState.value.readings.values.single().sequence)
        manager.disconnect(); idle(); assertTrue(manager.liveState.value.readings.isEmpty())
        clearInvocations(client)
        synchronize()
        assertEquals(1, manager.liveState.value.requestId)
    }
    @Test fun `gaps coalesce into bounded recovery`() {
        val (_, control) = synchronize()
        callback.onCharacteristicChanged(client, stateCharacteristic(), fixtureBytes("record.valid")); idle()
        idleFor(4)
        verify(client, never()).writeCharacteristic(eq(control), eq(byteArrayOf(2, 2, 0)), eq(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT))
        idleFor(1)
        verify(client).writeCharacteristic(eq(control), eq(byteArrayOf(2, 2, 0)), eq(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT))
        idleFor(6)
        assertTrue(manager.connectionState.value is ConnectionState.Error)
        assertTrue(manager.liveState.value.readings.isEmpty())
    }
    @Test fun `state epoch failure and late notification cannot change replacement session`() {
        subscribe()
        val state = stateCharacteristic()
        callback.onCharacteristicRead(client, state, fixtureBytes("image.1").also { it[4] = 0 }, 0); idle()
        assertTrue(manager.connectionState.value is ConnectionState.Error)
        assertTrue(manager.liveState.value.readings.isEmpty())
        nextClient = mock(BluetoothGatt::class.java); connect()
        callback.onCharacteristicChanged(client, state, fixtureBytes("record.valid")); idle()
        callback.onCharacteristicRead(client, state, fixtureBytes("image.1"), 0); idle()
        assertEquals(ConnectionState.Connecting, manager.connectionState.value)
        assertTrue(manager.liveState.value.readings.isEmpty())
    }
    @Test fun `missing CCCD is rejected without downgrade`() {
        protocolService()
        val svc = client.getService(PhysicalLinkProtocol.SERVICE)
        val replacement = BluetoothGattService(PhysicalLinkProtocol.SERVICE, 0)
        replacement.addCharacteristic(svc.getCharacteristic(PhysicalLinkProtocol.METADATA))
        replacement.addCharacteristic(svc.getCharacteristic(PhysicalLinkProtocol.CONTROL))
        replacement.addCharacteristic(BluetoothGattCharacteristic(PhysicalLinkProtocol.STATE, BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0))
        `when`(client.getService(PhysicalLinkProtocol.SERVICE)).thenReturn(replacement)
        services(); assertTrue(manager.connectionState.value is ConnectionState.Error)
    }
}
