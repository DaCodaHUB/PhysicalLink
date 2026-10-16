
package com.dacodahub.physicallink

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.dacodahub.physicallink.ble.BleConnectionManager
import com.dacodahub.physicallink.ble.BleScanner
import com.dacodahub.physicallink.ble.ConnectionState

class MainViewModel(application: Application) :
    AndroidViewModel(application) {

    private val scanner = BleScanner(application)

    val devices = scanner.devices

    fun startScan() {
        if (connectionManager.connectionState.value
                    is ConnectionState.Connected
        ) {
            return
        }

        connectionManager.clearConnectionError()
        scanner.startScan()
    }

    fun stopScan() = scanner.stopScan()

    private val connectionManager = BleConnectionManager(application)

    val connectionState = connectionManager.connectionState
    val readings = connectionManager.readings
    val capabilities = connectionManager.capabilities

    fun connect(address: String) {
        scanner.stopScan()
        connectionManager.connect(address)
    }

    fun disconnect() = connectionManager.disconnect()

    override fun onCleared() {
        scanner.stopScan()
        connectionManager.disconnect()
        super.onCleared()
    }
}
