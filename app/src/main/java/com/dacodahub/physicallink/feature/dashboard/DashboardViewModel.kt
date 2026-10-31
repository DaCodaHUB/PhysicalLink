package com.dacodahub.physicallink.feature.dashboard

import com.dacodahub.physicallink.feature.sensordetails.SensorDetailsInput
import com.dacodahub.physicallink.feature.sensordetails.SensorDetailsSessionGate
import androidx.lifecycle.SavedStateHandle
import com.dacodahub.physicallink.data.repository.DemoDeviceRepository
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
    realRepository: DeviceRepository,
    private val layouts: DashboardLayoutRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val isDemo: Boolean = savedStateHandle["demo"] ?: false
    private val demo = if (isDemo) DemoDeviceRepository(MonotonicClock { realRepository.monotonicNowMs() }) else null
    private val repository: DeviceRepository = demo ?: realRepository
    private var previousDemoJob: Job? = null
    private var demoObservers = 0
    private var demoJob: Job? = null
    suspend fun simulateDemoWhileVisible() {
        if (demo == null) return
        demoObservers++
        if (demoJob == null) {
            val previous = previousDemoJob
            demoJob = viewModelScope.launch { previous?.join(); demo.simulateWhileVisible() }
        }
        try { awaitCancellation() } finally {
            demoObservers--
            if (demoObservers == 0) { previousDemoJob = demoJob; demoJob?.cancel(); demoJob = null }
        }
    }

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

    private val loaded = repository.dashboardDeviceId.flatMapLatest { deviceId ->
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

    internal val detailsLayout = combine(loaded, repository.dashboardDeviceId, capabilities) { saved, id, metadata ->
        if (!saved.ready || saved.deviceId != id) null else saved.layout ?: defaultDashboard(id, metadata)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val persistenceStatus = combine(loaded, saveError) { layout, error -> layout to error }
    private val presentationInputs = combine(sensorValues, repository.sensorHistory, connectionState) { values, history, connection ->
        Triple(values, history, connection)
    }
    private val ticks = flow { while (currentCoroutineContext().isActive) { emit(repository.monotonicNowMs()); delay(1000) } }
    // Read the clock on every input too: a packet can arrive between one-second ticks.
    private val presentation = combine(presentationInputs, ticks) { inputs, _ -> inputs to repository.monotonicNowMs() }
    private val detailsSessionGate = SensorDetailsSessionGate()
    internal val sensorDetails = combine(presentation, capabilities, repository.dashboardDeviceId, repository.liveState, repository.manifest) {
            _, _, _, _, _ ->
        // GATT/session mutations run synchronously on main. Read one current snapshot rather than
        // mixing delayed combine emissions across a metadata/epoch change.
        detailsSessionGate.accept(SensorDetailsInput(repository.dashboardDeviceId.value, repository.capabilities.value, repository.sensorHistory.value,
            repository.connectionState.value, repository.sensorValues.value, repository.liveState.value,
            repository.manifest.value, repository.monotonicNowMs(), isDemo))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SensorDetailsInput(simulated = isDemo))
    val dashboard = combine(repository.dashboardDeviceId, capabilities, presentation, persistenceStatus, editing) {
            deviceId, metadata, presentationState, status, isEditing ->
        val (inputs, now) = presentationState
        val (values, history, connection) = inputs
        val (saved, error) = status
        val ready = saved.deviceId == deviceId && saved.ready
        val state = if (ready) dashboardState(deviceId, metadata, values, saved.layout, history, now,
            connection == ConnectionState.Connected) else DashboardState()
        val presented = if (isDemo) state.copy(widgets = state.widgets.map { it.copy(simulated = true) },
            hiddenWidgets = state.hiddenWidgets.map { it.copy(simulated = true) }) else state
        presented.copy(isEditing = isEditing, loadingLayout = !ready, persistenceError = saved.error ?: error?.takeIf { it.first == deviceId }?.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState(loadingLayout = true))

    init {
        // Only metadata changes trigger reconciliation; live sensor values never trigger writes.
        viewModelScope.launch {
            combine(loaded, capabilities, connectionState) { saved, metadata, connection -> Triple(saved, metadata, connection) }
                .collect { (saved, metadata, connection) ->
                    val id = saved.deviceId
                    if (id != null && id == repository.dashboardDeviceId.value && saved.ready &&
                        saved.layout == null && metadata?.version == 2 && connection == ConnectionState.Connected) {
                        save(id) { current -> current ?: defaultDashboard(id, metadata) }
                    }
                    if (id != null && id == repository.dashboardDeviceId.value && saved.ready &&
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
                if (repository.dashboardDeviceId.value == deviceId) saveError.value = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (repository.dashboardDeviceId.value == deviceId) saveError.value = deviceId to "Layout could not be saved. Try your change again."
            }
        }
    }

    private fun edit(transform: (DashboardLayout) -> DashboardLayout) {
        val id = repository.dashboardDeviceId.value?.takeIf { it.isNotBlank() } ?: return
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
    fun configureWidget(id: String, type: WidgetType, settings: WidgetSettings) {
        if (settings.validationError() != null || ((type == WidgetType.GAUGE || type == WidgetType.RANGE) && !settings.hasRange)) return
        edit { it.configureWidget(id, type, settings) }
    }
    internal fun configureDetailsWidget(source: SensorSource, widgetId: String, settings: WidgetSettings) {
        if (source.deviceId != repository.dashboardDeviceId.value || settings.validationError() != null) return
        edit { layout ->
            val current = layout.widgets.firstOrNull { it.id == widgetId && it.source == source } ?: return@edit layout
            if ((current.type == WidgetType.GAUGE || current.type == WidgetType.RANGE) && !settings.hasRange) return@edit layout
            layout.configureWidget(current.id, current.type, settings)
        }
    }

    fun useValueWidget(id: String) = edit { it.setWidgetType(id, WidgetType.VALUE) }
    fun hideWidget(id: String) = edit { it.setWidgetHidden(id, true) }
    fun restoreWidget(id: String) = edit { it.setWidgetHidden(id, false) }
    fun duplicateWidget(id: String) {
        val newId = UUID.randomUUID().toString()
        edit { it.duplicateWidget(id, newId) }
    }
    fun addWidget(source: SensorSource) {
        if (source.deviceId != repository.dashboardDeviceId.value ||
            capabilities.value?.validDashboardCapabilities()?.none { it.id == source.capabilityId } != false) return
        val id = UUID.randomUUID().toString()
        edit { it.addValueWidget(id, source) }
    }

    fun setLayout(layout: DashboardLayout) {
        val id = repository.dashboardDeviceId.value ?: return
        if (layout.widgets.any { it.source.deviceId != id }) return
        save(id) { layout }
    }

    fun resetLayout() {
        val id = repository.dashboardDeviceId.value ?: return
        save(id) { null }
    }

    fun disconnect() = repository.disconnect()
}
