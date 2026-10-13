package com.dacodahub.physicallink.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiscoveredDevice(
    val name: String,
    val address: String,
    val rssi: Int
)

@SuppressLint("MissingPermission")
class BleScanner(context: Context) {

    private val bluetoothManager =
        context.getSystemService(BluetoothManager::class.java)

    private val scanner
        get() = bluetoothManager.adapter?.bluetoothLeScanner

    private val _devices =
        MutableStateFlow<List<DiscoveredDevice>>(emptyList())

    val devices = _devices.asStateFlow()

    private var scanning = false

    private val callback = object : ScanCallback() {
        override fun onScanResult(
            callbackType: Int,
            result: ScanResult
        ) {
            val name = result.scanRecord?.deviceName
                ?: result.device.name
                ?: return

            if (name != "PhysicalLink Soil") return

            val device = DiscoveredDevice(
                name = name,
                address = result.device.address,
                rssi = result.rssi
            )

            _devices.value =
                _devices.value
                    .filterNot { it.address == device.address } + device
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            android.util.Log.e(
                "PhysicalLink",
                "BLE scan failed: $errorCode"
            )
        }
    }

    fun startScan() {
        if (scanning) return

        val bleScanner = scanner ?: return

        _devices.value = emptyList()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanning = true

        try {
            bleScanner.startScan(null, settings, callback)
        } catch (e: SecurityException) {
            scanning = false
            android.util.Log.e("PhysicalLink", "Missing BLE permission", e)
        }
    }

    fun stopScan() {
        if (!scanning) return
        scanning = false
        try {
            scanner?.stopScan(callback)
        } catch (e: SecurityException) {
            android.util.Log.e("PhysicalLink", "Cannot stop scan", e)
        }
    }
}
