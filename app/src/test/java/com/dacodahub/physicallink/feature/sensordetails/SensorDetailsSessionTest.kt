package com.dacodahub.physicallink.feature.sensordetails

import androidx.lifecycle.*
import com.dacodahub.physicallink.FakeDashboardLayoutRepository
import com.dacodahub.physicallink.feature.dashboard.DashboardViewModel
import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.domain.repository.DeviceRepository
import com.dacodahub.physicallink.data.repository.DemoDeviceRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class SensorDetailsSessionTest {
    private class Repository(val clock: () -> Long) : DeviceRepository {
        override val connectedDeviceId = MutableStateFlow<String?>("board")
        override val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
        override val scanState = MutableStateFlow<ScanState>(ScanState.Idle)
        override val devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
        override val capabilities = MutableStateFlow<DeviceCapabilities?>(DeviceCapabilities(1, "Board", listOf(DeviceCapability("id", "fixture", "u", "number"))))
        override val sensorHistory = MutableStateFlow(SensorHistorySnapshot("board", 1, true,
            mapOf(SensorSource("board", "id") to SensorTimeline(listOf(NumericSample(0, 4.0, 0)), 0, 4.0, true))))
        override val sensorValues = MutableStateFlow(mapOf("id" to "4"))
        override fun monotonicNowMs() = clock()
        var operations = 0
        override fun startScan() { operations++ }; override fun stopScan() { operations++ }
        override fun connect(address: String) { operations++ }; override fun disconnect() { operations++ }
        override fun refreshBluetoothState() { operations++ }
    }
    @Test fun `details projection observes the same history and never starts another BLE operation`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val repository = Repository { testScheduler.currentTime }
            val vm = DashboardViewModel(repository, FakeDashboardLayoutRepository(), SavedStateHandle())
            store.put("vm", vm)
            val before = repository.sensorHistory.value
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.sensorDetails.collect() }
            runCurrent()
            assertSame(before, vm.sensorDetails.value.history)
            assertEquals(1, sensorDetailsState(SensorSource("board", "id"), vm.sensorDetails.value).statistics!!.count)
            repository.sensorHistory.value = before.copy(sessionId = 2, sensors = emptyMap())
            runCurrent()
            assertNull(sensorDetailsState(SensorSource("board", "id"), vm.sensorDetails.value).statistics)
            repository.connectionState.value = ConnectionState.ConnectionLost
            runCurrent()
            assertEquals("Disconnected", sensorDetailsState(SensorSource("board", "id"), vm.sensorDetails.value).status)
            assertEquals(0, repository.operations)
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }
    @Test fun `overlapping dashboard and details consumers share one demo session and stop after both leave`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val repository = Repository { testScheduler.currentTime }
            val vm = DashboardViewModel(repository, FakeDashboardLayoutRepository(), SavedStateHandle(mapOf("demo" to true)))
            store.put("vm", vm)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.sensorDetails.collect() }
            val dashboard = backgroundScope.launch { vm.simulateDemoWhileVisible() }
            runCurrent()
            val first = vm.sensorDetails.value.history
            val source = SensorSource(DemoDeviceRepository.DEVICE_ID, DemoDeviceRepository.CAPABILITIES.capabilities.first().id)
            assertTrue(first.active)
            advanceTimeBy(2000); runCurrent()
            val details = backgroundScope.launch { vm.simulateDemoWhileVisible() }
            runCurrent(); dashboard.cancelAndJoin(); runCurrent()
            advanceTimeBy(1000); runCurrent()
            assertEquals(first.sessionId, vm.sensorDetails.value.history.sessionId)
            assertTrue(vm.sensorDetails.value.history.sensors.getValue(source).samples.size >= 4)
            details.cancelAndJoin(); runCurrent()
            assertFalse(vm.sensorDetails.value.history.active)
            assertEquals(0, repository.operations)
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }
}
