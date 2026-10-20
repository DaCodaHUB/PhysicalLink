package com.dacodahub.physicallink.feature.dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.dacodahub.physicallink.FakeDashboardLayoutRepository
import com.dacodahub.physicallink.data.repository.DemoDeviceRepository
import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.domain.repository.DeviceRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoDashboardViewModelTest {
    private class RealDevice(private val clock: () -> Long) : DeviceRepository {
        override val connectedDeviceId = MutableStateFlow<String?>("AA:BB:CC:DD:EE:FF")
        override val scanState = MutableStateFlow<ScanState>(ScanState.Idle)
        override val devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
        override val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
        override val capabilities = MutableStateFlow<DeviceCapabilities?>(DeviceCapabilities(1, "Real board", listOf(DeviceCapability("real", "5678", "raw", "number"))))
        override val sensorValues = MutableStateFlow(mapOf("real" to "999"))
        override val sensorHistory = MutableStateFlow(SensorHistorySnapshot())
        override fun monotonicNowMs() = clock()
        var operations = 0
        override fun startScan() { operations++ }
        override fun stopScan() { operations++ }
        override fun connect(address: String) { operations++ }
        override fun disconnect() { operations++ }
        override fun refreshBluetoothState() { operations++ }
    }

    @Test fun `demo entry editing exit and recreated navigation state preserve layouts without touching real BLE`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val real = RealDevice { testScheduler.currentTime }
            val layouts = FakeDashboardLayoutRepository()
            val realLayout = defaultDashboard(real.connectedDeviceId.value, real.capabilities.value)
            layouts.update(real.connectedDeviceId.value!!) { realLayout }.await()
            fun create() = DashboardViewModel(real, layouts, SavedStateHandle(mapOf("demo" to true)))
            val demo = create(); store.put("demo", demo)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { demo.dashboard.collect() }
            val simulation = backgroundScope.launch { demo.simulateDemoWhileVisible() }
            runCurrent()
            assertTrue(demo.isDemo)
            assertEquals(DemoDeviceRepository.DEVICE_ID, demo.deviceId.value)
            assertEquals("Demo Sensor Station", demo.dashboard.value.deviceName)
            assertEquals(4, demo.dashboard.value.widgets.size)
            assertTrue(demo.dashboard.value.widgets.all { it.simulated && it.freshness == ReadingFreshness.LIVE })
            val id = demo.dashboard.value.widgets.first().widget.id
            val settings = WidgetSettings(300, 18.0, 30.0, 20.0, 28.0, 3, 5)
            demo.setEditing(true)
            demo.configureWidget(id, WidgetType.GAUGE, settings)
            demo.resizeWidget(id, 2)
            demo.duplicateWidget(id)
            demo.hideWidget(id)
            runCurrent()
            assertTrue(demo.dashboard.value.isEditing)
            assertEquals(settings, demo.dashboard.value.hiddenWidgets.single().widget.settings)
            assertEquals(4, demo.dashboard.value.widgets.size)
            assertEquals(2, demo.dashboard.value.widgets.last().widget.columnSpan)
            advanceTimeBy(1000); runCurrent()
            assertTrue(demo.dashboard.value.widgets.all { it.freshness == ReadingFreshness.LIVE })
            demo.restoreWidget(id); demo.moveWidgetBy(id, 1); runCurrent()
            val saved = layouts.observe(DemoDeviceRepository.DEVICE_ID).first()!!
            simulation.cancelAndJoin()
            store.clear()
            assertEquals(0, real.operations)
            assertEquals(mapOf("real" to "999"), real.sensorValues.value)
            assertEquals(ConnectionState.Connected, real.connectionState.value)
            assertEquals(realLayout, layouts.observe(real.connectedDeviceId.value!!).first())
            val recreated = create(); store.put("restored", recreated)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { recreated.dashboard.collect() }
            runCurrent()
            assertTrue(recreated.isDemo)
            assertEquals(saved.widgets, recreated.dashboard.value.widgets.map { it.widget })
            recreated.resetLayout(); runCurrent()
            assertNull(layouts.observe(DemoDeviceRepository.DEVICE_ID).first())
            assertEquals(4, recreated.dashboard.value.widgets.size)
            assertEquals(realLayout, layouts.observe(real.connectedDeviceId.value!!).first())
        } finally {
            backgroundScope.coroutineContext.cancelChildren()
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test fun `normal dashboard still observes only the real repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val real = RealDevice { testScheduler.currentTime }
            val viewModel = DashboardViewModel(real, FakeDashboardLayoutRepository(), SavedStateHandle())
            store.put("real", viewModel)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.dashboard.collect() }
            runCurrent()
            assertFalse(viewModel.isDemo)
            assertSame(real.sensorValues, viewModel.sensorValues)
            assertEquals("Real board", viewModel.dashboard.value.deviceName)
            assertTrue(viewModel.dashboard.value.widgets.none { it.simulated })
        } finally {
            backgroundScope.coroutineContext.cancelChildren()
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
