package com.dacodahub.physicallink.data.ble

import android.Manifest
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import com.dacodahub.physicallink.domain.model.ScanState
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class BleScannerTest {
    private val context = mock(Context::class.java)
    private val bluetooth = mock(BluetoothManager::class.java)
    private val adapter = mock(BluetoothAdapter::class.java)
    private val platformScanner = mock(BluetoothLeScanner::class.java)
    private val callbacks = mutableListOf<ScanCallback>()
    private lateinit var scanner: BleScanner
    @Before fun setup() {
        `when`(context.getSystemService(BluetoothManager::class.java)).thenReturn(bluetooth)
        `when`(bluetooth.adapter).thenReturn(adapter)
        `when`(adapter.isEnabled).thenReturn(true)
        `when`(adapter.bluetoothLeScanner).thenReturn(platformScanner)
        doAnswer { callbacks += it.getArgument<ScanCallback>(2); null }.`when`(platformScanner)
            .startScan(isNull(), any(ScanSettings::class.java), any(ScanCallback::class.java))
        scanner = BleScanner(context)
    }
    @After fun cleanup() { scanner.stopScan(); idle() }
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun result(): ScanResult {
        val device = mock(BluetoothDevice::class.java)
        val record = mock(ScanRecord::class.java)
        val result = mock(ScanResult::class.java)
        `when`(record.deviceName).thenReturn("PhysicalLink Soil")
        `when`(device.address).thenReturn("AA:BB:CC:DD:EE:FF")
        `when`(result.scanRecord).thenReturn(record)
        `when`(result.device).thenReturn(device)
        return result
    }
    @Test fun `scanner acquisition permission failure is reported instead of escaping`() {
        `when`(adapter.bluetoothLeScanner).thenThrow(SecurityException("revoked"))
        scanner.startScan(); idle()
        assertTrue(scanner.state.value is ScanState.Error)
        assertTrue(callbacks.isEmpty())
    }
    @Test fun `disabled and unavailable Bluetooth report actionable errors`() {
        `when`(adapter.isEnabled).thenReturn(false)
        scanner.startScan(); idle()
        assertTrue((scanner.state.value as ScanState.Error).message.contains("Turn it on"))
        `when`(bluetooth.adapter).thenReturn(null)
        scanner.startScan(); idle()
        assertTrue(scanner.state.value is ScanState.Error)
    }
    @Test fun `asynchronous scan failure is visible and permits retry`() {
        scanner.startScan(); idle()
        callbacks.single().onScanFailed(ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED); idle()
        assertTrue((scanner.state.value as ScanState.Error).message.contains("retry"))
        scanner.startScan(); idle()
        assertEquals(2, callbacks.size)
        assertEquals(ScanState.Scanning, scanner.state.value)
    }
    @Test fun `late results and failures from a stopped scan cannot mutate a newer scan`() {
        scanner.startScan(); idle()
        val old = callbacks.single()
        scanner.stopScan(); scanner.startScan(); idle()
        old.onScanResult(0, result()); old.onScanFailed(2); idle()
        assertTrue(scanner.devices.value.isEmpty())
        assertEquals(ScanState.Scanning, scanner.state.value)
        callbacks.last().onScanResult(0, result()); idle()
        assertEquals(1, scanner.devices.value.size)
    }
    @Test fun `scan timeout cancels registration and no duplicate scan is started`() {
        scanner.startScan(); scanner.startScan(); idle()
        assertEquals(1, callbacks.size)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16))
        assertEquals(ScanState.Idle, scanner.state.value)
        verify(platformScanner).stopScan(callbacks.single())
        callbacks.single().onScanResult(0, result()); idle()
        assertTrue(scanner.devices.value.isEmpty())
    }
    @Test fun `adapter shutdown stops active scan`() {
        scanner.startScan(); idle()
        `when`(adapter.isEnabled).thenReturn(false)
        scanner.refreshEnvironment(); idle()
        assertTrue(scanner.state.value is ScanState.Error)
        verify(platformScanner).stopScan(callbacks.single())
    }
    @Test fun `revoked scan permission is detected on foreground validation`() {
        scanner.startScan(); idle()
        `when`(context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)).thenReturn(PackageManager.PERMISSION_DENIED)
        scanner.refreshEnvironment(); idle()
        assertTrue(scanner.state.value is ScanState.Error)
        verify(platformScanner).stopScan(callbacks.single())
    }
    @Test fun `revoked permission while resolving a result fails safely`() {
        scanner.startScan(); idle()
        val result = result()
        `when`(result.device.address).thenThrow(SecurityException("revoked"))
        callbacks.single().onScanResult(0, result); idle()
        assertTrue(scanner.state.value is ScanState.Error)
        assertTrue(scanner.devices.value.isEmpty())
    }
    @Test fun `stop permission failure still invalidates callbacks`() {
        scanner.startScan(); idle()
        doThrow(SecurityException("revoked")).`when`(platformScanner).stopScan(callbacks.single())
        scanner.stopScan(); idle()
        callbacks.single().onScanResult(0, result()); idle()
        assertEquals(ScanState.Idle, scanner.state.value)
        assertTrue(scanner.devices.value.isEmpty())
    }
}
