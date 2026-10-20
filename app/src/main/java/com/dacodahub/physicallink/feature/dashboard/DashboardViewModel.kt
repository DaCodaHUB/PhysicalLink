package com.dacodahub.physicallink.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.domain.repository.DashboardLayoutRepository
import com.dacodahub.physicallink.domain.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val layouts: DashboardLayoutRepository
) : ViewModel() {
    val deviceId = repository.connectedDeviceId
    val devices = repository.devices
    val connectionState = repository.connectionState
    val capabilities = repository.capabilities
    val sensorValues = repository.sensorValues
    private val editing = MutableStateFlow(false)
    private val saveError = MutableStateFlow<Pair<String, String>?>(null)
    private data class LoadedLayout(
        val deviceId: String?, val layout: DashboardLayout? = null,
        val ready: Boolean = false, val error: String? = null
    )

    private val loaded = repository.connectedDeviceId.flatMapLatest { deviceId ->
        if (deviceId.isNullOrBlank()) flowOf(LoadedLayout(null))
        else layouts.observe(deviceId).map { LoadedLayout(deviceId, it, ready = true) }
            .onStart { emit(LoadedLayout(deviceId)) }
            .retryWhen { error, _ ->
                if (error is IOException) {
                    emit(LoadedLayout(deviceId, error = "Unable to load layout. Retrying..."))
                    delay(1000)
                    true
                } else false
            }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(LoadedLayout(deviceId, error = "Unable to load layout."))
            }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LoadedLayout(null))

    private val persistenceStatus = combine(loaded, saveError) { layout, error -> layout to error }
    val dashboard = combine(repository.connectedDeviceId, capabilities, sensorValues, persistenceStatus, editing) {
            deviceId, metadata, values, status, isEditing ->
        val (saved, error) = status
        val ready = saved.deviceId == deviceId && saved.ready
        val state = if (ready) dashboardState(deviceId, metadata, values, saved.layout) else DashboardState()
        state.copy(isEditing = isEditing, loadingLayout = !ready, persistenceError = saved.error ?: error?.takeIf { it.first == deviceId }?.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState(loadingLayout = true))

    init {
        // Only metadata changes trigger reconciliation; live sensor values never trigger writes.
        viewModelScope.launch {
            combine(loaded, capabilities, connectionState) { saved, metadata, connection -> Triple(saved, metadata, connection) }
                .collect { (saved, metadata, connection) ->
                    val id = saved.deviceId
                    if (id != null && id == repository.connectedDeviceId.value && saved.ready &&
                        saved.layout != null && metadata != null && connection == ConnectionState.Connected &&
                        saved.layout.withDiscoveredSensors(id, metadata) != saved.layout) {
                        save(id) { current -> current?.withDiscoveredSensors(id, metadata) }
                    }
                }
        }
    }

    fun setEditing(value: Boolean) { editing.value = value }

    private fun save(deviceId: String, transform: (DashboardLayout?) -> DashboardLayout?) {
        val result = layouts.update(deviceId, transform)
        viewModelScope.launch {
            try {
                result.await()
                if (repository.connectedDeviceId.value == deviceId) saveError.value = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (repository.connectedDeviceId.value == deviceId) saveError.value = deviceId to "Layout could not be saved. Try your change again."
            }
        }
    }

    private fun edit(transform: (DashboardLayout) -> DashboardLayout) {
        val id = repository.connectedDeviceId.value?.takeIf { it.isNotBlank() } ?: return
        val metadata = capabilities.value
        save(id) { current ->
            val base = current ?: defaultDashboard(id, metadata)
            val next = transform(base)
            if (next == base) current else next
        }
    }

    fun moveWidget(id: String, targetId: String) = edit { it.moveWidget(id, targetId) }
    fun moveWidgetBy(id: String, direction: Int) = edit { it.moveWidgetBy(id, direction) }
    fun toggleWidth(id: String) = edit { it.toggleWidgetWidth(id) }
    fun resizeWidget(id: String, columns: Int) = edit { it.resizeWidget(id, columns) }
    fun useValueWidget(id: String) = edit { it.setWidgetType(id, WidgetType.VALUE) }
    fun hideWidget(id: String) = edit { it.setWidgetHidden(id, true) }
    fun restoreWidget(id: String) = edit { it.setWidgetHidden(id, false) }
    fun addWidget(source: SensorSource) {
        if (source.deviceId != repository.connectedDeviceId.value ||
            capabilities.value?.validDashboardCapabilities()?.none { it.id == source.capabilityId } != false) return
        val id = UUID.randomUUID().toString()
        edit { it.addValueWidget(id, source) }
    }

    fun setLayout(layout: DashboardLayout) {
        val id = repository.connectedDeviceId.value ?: return
        if (layout.widgets.any { it.source.deviceId != id }) return
        save(id) { layout }
    }

    fun resetLayout() {
        val id = repository.connectedDeviceId.value ?: return
        save(id) { null }
    }

    fun disconnect() = repository.disconnect()
}
