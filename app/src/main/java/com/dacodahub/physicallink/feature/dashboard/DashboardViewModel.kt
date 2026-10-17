package com.dacodahub.physicallink.feature.dashboard

import androidx.lifecycle.ViewModel
import com.dacodahub.physicallink.domain.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(private val repository: DeviceRepository) : ViewModel() {
    val connectionState = repository.connectionState
    val capabilities = repository.capabilities
    val sensorValues = repository.sensorValues

    fun disconnect() = repository.disconnect()
}
