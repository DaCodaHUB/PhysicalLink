package com.dacodahub.physicallink.feature.sensordetails

import androidx.lifecycle.*
import com.dacodahub.physicallink.FakeDashboardLayoutRepository
import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.domain.repository.DeviceRepository
import com.dacodahub.physicallink.feature.dashboard.DashboardViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

internal class DetailsWidgetRepository : DeviceRepository {
    val source = SensorSource("stable-board", "channel")
    override val dashboardDeviceId = MutableStateFlow<String?>(source.deviceId)
    override val connectedDeviceId = MutableStateFlow<String?>("address-a")
    override val capabilities = MutableStateFlow<DeviceCapabilities?>(DeviceCapabilities(1, "Project", listOf(DeviceCapability("channel", "fixture", "\u03BCg", "number", "Sensor Alpha"))))
    override val sensorValues = MutableStateFlow(mapOf("channel" to "12.345"))
    override val sensorHistory = MutableStateFlow(SensorHistorySnapshot(source.deviceId, 1, true,
        mapOf(source to SensorTimeline(listOf(NumericSample(0, 10.0, 0), NumericSample(1000, 12.345, 0)), 1000, 12.345, true))))
    override val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
    override val scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    override val devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override fun monotonicNowMs() = 1000L
    override fun startScan() {}; override fun stopScan() {}; override fun connect(address: String) {}; override fun refreshBluetoothState() {}; override fun disconnect() {}
    fun layout() = DashboardLayout(listOf(
        DashboardWidget("gauge", WidgetType.GAUGE, source, settings = WidgetSettings(minimum = 0.0, maximum = 20.0, precision = 3)),
        DashboardWidget("chart", WidgetType.CHART, source, columnSpan = 2,
            settings = WidgetSettings(chartWindowSeconds = 300, precision = 1, chartMinimum = -5.0, chartMaximum = 15.0))))
}
@OptIn(ExperimentalCoroutinesApi::class)
class DetailsWidgetSyncTest {
    @Test fun `details saves only matching widget settings and never recreates deleted instances after reconnect`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val repository = DetailsWidgetRepository()
            val layouts = FakeDashboardLayoutRepository()
            val original = repository.layout()
            layouts.update(repository.source.deviceId) { original }.await()
            val vm = DashboardViewModel(repository, layouts, SavedStateHandle()); store.put("vm", vm)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.detailsLayout.collect() }
            runCurrent()
            val changed = original.widgets.last().settings.copy(chartMinimum = 1.0, chartMaximum = null, precision = 5)
            vm.configureDetailsWidget(repository.source, "chart", changed); runCurrent()
            var saved = layouts.observe(repository.source.deviceId).first()!!
            assertEquals(original.widgets.first(), saved.widgets.first())
            assertEquals(original.widgets.last().copy(settings = changed), saved.widgets.last())
            repository.connectionState.value = ConnectionState.ConnectionLost
            repository.connectedDeviceId.value = "address-b"
            repository.connectionState.value = ConnectionState.Connected
            runCurrent()
            assertEquals(changed, vm.detailsLayout.value!!.widgets.last().settings)
            layouts.update(repository.source.deviceId) { it!!.copy(widgets = it.widgets.filterNot { widget -> widget.id == "chart" }) }.await()
            runCurrent()
            vm.configureDetailsWidget(repository.source, "chart", changed.copy(precision = 6)); runCurrent()
            saved = layouts.observe(repository.source.deviceId).first()!!
            assertEquals(listOf(original.widgets.first()), saved.widgets)
            vm.configureDetailsWidget(repository.source, "gauge", WidgetSettings(minimum = Double.NaN, maximum = 10.0)); runCurrent()
            assertEquals(saved, layouts.observe(repository.source.deviceId).first())
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }
}
