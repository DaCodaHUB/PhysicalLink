package com.dacodahub.physicallink.data.ble

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dacodahub.physicallink.domain.model.DiscoveredDevice
import com.dacodahub.physicallink.domain.model.ScanState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@SuppressLint("MissingPermission")
class BleScanner(private val context: Context) {
    private val bluetoothManager: BluetoothManager? = context.getSystemService(BluetoothManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices = _devices.asStateFlow()
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state = _state.asStateFlow()
    private var activeCallback: ScanCallback? = null
    private var activeScanner: BluetoothLeScanner? = null
    private var timeout: Runnable? = null

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == handler.looper) block() else handler.post { block() }
    }

    fun startScan() = onMain {
        if (activeCallback != null) return@onMain
        try {
            checkPermissions()
            val adapter = checkNotNull(bluetoothManager?.adapter) { "Bluetooth unavailable on this device." }
            check(adapter.isEnabled) { "Bluetooth is disabled. Turn it on, then scan again." }
            val scanner = checkNotNull(adapter.bluetoothLeScanner) { "Bluetooth scanner unavailable. Turn Bluetooth on and retry." }
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    handler.post {
                        if (activeCallback !== this) return@post
                        try {
                            val name = result.scanRecord?.deviceName ?: result.device.name ?: return@post
                            if (name != "PhysicalLink Soil") return@post
                            val device = DiscoveredDevice(name, result.device.address, result.rssi)
                            _devices.value = _devices.value.filterNot { it.address == device.address } + device
                        } catch (_: SecurityException) { finish(permissionError()) }
                    }
                }
                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
                }
                override fun onScanFailed(errorCode: Int) {
                    handler.post {
                        if (activeCallback === this) finish(ScanState.Error(
                            "Scan failed (code $errorCode). Stop other Bluetooth scans, wait a moment, then retry."
                        ))
                    }
                }
            }
            activeScanner = scanner
            activeCallback = callback
            _devices.value = emptyList()
            _state.value = ScanState.Scanning
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            val stop = Runnable { if (activeCallback === callback) finish(ScanState.Idle) }
            timeout = stop
            handler.postDelayed(stop, 15_000)
        } catch (_: SecurityException) { finish(permissionError()) }
        catch (error: IllegalStateException) { finish(ScanState.Error(error.message ?: "Cannot scan. Turn Bluetooth on and retry.")) }
    }

    fun stopScan(clearError: Boolean = false) = onMain {
        // Lifecycle cleanup must not erase an already-completed failure diagnostic.
        if (activeCallback != null || clearError) finish(ScanState.Idle)
    }

    fun refreshEnvironment() = onMain {
        if (activeCallback == null) return@onMain
        try {
            checkPermissions()
            if (bluetoothManager?.adapter?.isEnabled != true)
                finish(ScanState.Error("Bluetooth is disabled. Turn it on, then scan again."))
        } catch (_: SecurityException) { finish(permissionError()) }
    }

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= 31)
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (permissions.any { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED })
            throw SecurityException("Bluetooth scan permissions missing")
    }

    private fun permissionError() = ScanState.Error("Bluetooth permission denied. Allow Nearby devices (or Location on Android 11 and earlier) in app settings, then scan again.")

    private fun finish(state: ScanState) {
        val callback = activeCallback
        val scanner = activeScanner
        activeCallback = null // Invalidate before stopScan can deliver late results or failures.
        activeScanner = null
        timeout?.let(handler::removeCallbacks)
        timeout = null
        _state.value = state
        if (callback != null && scanner != null) {
            try { scanner.stopScan(callback) }
            catch (error: RuntimeException) { Log.w("PhysicalLink", "Cannot stop scan", error) }
        }
    }
}
