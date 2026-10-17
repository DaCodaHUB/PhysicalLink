package com.dacodahub.physicallink.feature.devices

import androidx.lifecycle.ViewModel
import com.dacodahub.physicallink.domain.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(private val repository: DeviceRepository) : ViewModel() {
    val devices = repository.devices
    val connectionState = repository.connectionState

    fun startScan() = repository.startScan()
    fun stopScan() = repository.stopScan()
    fun connect(address: String) = repository.connect(address)
    fun disconnect() = repository.disconnect()
}
