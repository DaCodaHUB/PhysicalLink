
package com.dacodahub.physicallink

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.dacodahub.physicallink.ble.BleScanner

class MainViewModel(application: Application) :
    AndroidViewModel(application) {

    private val scanner = BleScanner(application)

    val devices = scanner.devices

    fun startScan() = scanner.startScan()

    fun stopScan() = scanner.stopScan()

    override fun onCleared() {
        scanner.stopScan()
        super.onCleared()
    }
}
