package com.dacodahub.physicallink.data.repository

import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanSettings
import android.content.*
import android.os.Handler
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

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class BleDeviceRepositoryTest {
    private val context = mock(Context::class.java)
    private val bluetooth = mock(BluetoothManager::class.java)
    private val adapter = mock(BluetoothAdapter::class.java)
    private val device = mock(BluetoothDevice::class.java)
    private val client = mock(BluetoothGatt::class.java)
    private val scanner = mock(BluetoothLeScanner::class.java)
    private lateinit var receiver: BroadcastReceiver
    private lateinit var repository: BleDeviceRepository
    @Before fun setup() {
        `when`(context.getSystemService(BluetoothManager::class.java)).thenReturn(bluetooth)
        `when`(bluetooth.adapter).thenReturn(adapter)
        `when`(adapter.isEnabled).thenReturn(true)
        `when`(adapter.bluetoothLeScanner).thenReturn(scanner)
        `when`(adapter.getRemoteDevice(anyString())).thenReturn(device)
        `when`(device.connectGatt(eq(context), eq(false), any(BluetoothGattCallback::class.java), eq(BluetoothDevice.TRANSPORT_LE)))
            .thenReturn(client)
        doAnswer { receiver = it.getArgument(0); null }.`when`(context).registerReceiver(
            any(BroadcastReceiver::class.java), any(IntentFilter::class.java), isNull(), isNull<Handler>(), anyInt())
        repository = BleDeviceRepository(context)
    }
    @After fun cleanup() { repository.disconnect(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6)) }
    @Test fun `fresh process starts disconnected with no selected device readings history or scans`() {
        repository.refreshBluetoothState()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(ConnectionState.Disconnected, repository.connectionState.value)
        assertNull(repository.connectedDeviceId.value)
        assertNull(repository.dashboardDeviceId.value)
        assertNull(repository.manifest.value)
        assertNull(repository.capabilities.value)
        assertTrue(repository.sensorValues.value.isEmpty())
        assertTrue(repository.sensorHistory.value.sensors.isEmpty())
        assertFalse(repository.sensorHistory.value.active)
        assertTrue(repository.devices.value.isEmpty())
        assertEquals(com.dacodahub.physicallink.domain.model.ScanState.Idle, repository.scanState.value)
        verify(adapter, never()).getRemoteDevice(anyString())
        verify(scanner, never()).startScan(isNull(), any(ScanSettings::class.java), any(ScanCallback::class.java))
    }
    @Test fun `adapter broadcast closes a session without reconnecting`() {
        repository.connect("AA:BB:CC:DD:EE:FF")
        `when`(adapter.isEnabled).thenReturn(false)
        receiver.onReceive(context, Intent(BluetoothAdapter.ACTION_STATE_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
        verify(client).close()
        assertTrue(repository.connectionState.value is ConnectionState.Error)
        assertEquals("AA:BB:CC:DD:EE:FF", repository.connectedDeviceId.value)
        `when`(adapter.isEnabled).thenReturn(true)
        receiver.onReceive(context, Intent(BluetoothAdapter.ACTION_STATE_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
        verify(device, times(1)).connectGatt(eq(context), eq(false), any(BluetoothGattCallback::class.java), eq(BluetoothDevice.TRANSPORT_LE))
    }
    @Test fun `scan cannot be started during an in flight connection`() {
        repository.connect("AA:BB:CC:DD:EE:FF")
        repository.startScan()
        shadowOf(Looper.getMainLooper()).idle()
        verify(scanner, never()).startScan(isNull(), any(ScanSettings::class.java), any(ScanCallback::class.java))
        assertEquals(ConnectionState.Connecting, repository.connectionState.value)
    }
    @Test fun `queued background connect and scan are serialized with the connection guard`() {
        val thread = Thread { repository.connect("AA:BB:CC:DD:EE:FF"); repository.startScan() }
        thread.start(); thread.join()
        shadowOf(Looper.getMainLooper()).idle()
        verify(scanner, never()).startScan(isNull(), any(ScanSettings::class.java), any(ScanCallback::class.java))
        assertEquals(ConnectionState.Connecting, repository.connectionState.value)
    }

}
