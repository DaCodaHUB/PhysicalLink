package com.dacodahub.physicallink.feature.devices

import androidx.lifecycle.viewModelScope
import com.dacodahub.physicallink.domain.repository.OnboardingRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.logging.Logger
import java.util.logging.Level
import androidx.lifecycle.ViewModel
import com.dacodahub.physicallink.domain.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(private val repository: DeviceRepository, private val onboarding: OnboardingRepository) : ViewModel() {
    val showIntroduction = onboarding.introductionDismissed.map { !it }.catch { error ->
        if (error is CancellationException) throw error
        Logger.getLogger("Onboarding").log(Level.WARNING, "Unable to load introduction preference", error)
        emit(false) // Help and every normal action stay available if preferences cannot load.
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val demoExplored = onboarding.demoExplored.catch { error ->
        if (error is CancellationException) throw error
        Logger.getLogger("Onboarding").log(Level.WARNING, "Unable to load demo preference", error)
        emit(false)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun markDemoExplored() {
        val save = onboarding.markDemoExplored()
        viewModelScope.launch {
            try { save.await() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Logger.getLogger("Onboarding").log(Level.WARNING, "Unable to save demo preference", error) }
        }
    }

    fun dismissIntroduction() {
        val save = onboarding.dismissIntroduction()
        viewModelScope.launch {
            try { save.await() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Logger.getLogger("Onboarding").log(Level.WARNING, "Unable to save introduction dismissal", error) }
        }
    }
    val deviceId = repository.connectedDeviceId
    val liveState = repository.liveState
    val manifest = repository.manifest
    val capabilities = repository.capabilities
    val scanState = repository.scanState
    val devices = repository.devices
    val connectionState = repository.connectionState

    fun startScan() = repository.startScan()
    fun stopScan() = repository.stopScan()
    fun connect(address: String) = repository.connect(address)
    fun disconnect() = repository.disconnect()
}
