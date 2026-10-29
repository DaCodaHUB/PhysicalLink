package com.dacodahub.physicallink.feature.dashboard

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModelStore
import com.dacodahub.physicallink.data.repository.DataStoreDashboardLayoutRepository
import com.dacodahub.physicallink.createTestDashboardDataStore
import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.domain.repository.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardPersistenceViewModelTest {
    @get:Rule val temporary = TemporaryFolder()
    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val viewModels = ViewModelStore()
    @Before fun setMain() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun close() = runBlocking {
        viewModels.clear()
        worker.coroutineContext.job.cancelAndJoin()
        Dispatchers.resetMain()
    }
    private fun metadata(vararg ids: String) = DeviceCapabilities(1, "Board", ids.map {
        DeviceCapability(it, "uuid-$it", "C", "number")
    })
    private fun repository(name: String = "test") = DataStoreDashboardLayoutRepository(createTestDashboardDataStore(worker) {
        File(temporary.root, "$name.preferences_pb")
    }, worker)
    private fun vm(devices: Devices, layouts: DashboardLayoutRepository, key: String = "vm") =
        DashboardViewModel(devices, layouts, androidx.lifecycle.SavedStateHandle()).also { viewModels.put(key, it) }
    private suspend fun DashboardViewModel.awaitState(predicate: (DashboardState) -> Boolean): DashboardState =
        withTimeout(5000) { dashboard.first(predicate) }

    @Test fun `late load for previous device cannot replace current device dashboard`() = runBlocking {
        val storage = repository()
        val gate = CompletableDeferred<Unit>()
        val old = DashboardLayout(listOf(DashboardWidget("old-A", WidgetType.VALUE, SensorSource("A", "temperature"))))
        storage.update("A") { old }.await()
        val delayed = object : DashboardLayoutRepository by storage {
            override fun observe(deviceId: String) = storage.observe(deviceId).onEach { if (deviceId == "A") gate.await() }
        }
        val devices = Devices(metadata("temperature"))
        val dashboard = vm(devices, delayed)
        assertTrue(dashboard.awaitState { it.loadingLayout }.widgets.isEmpty())
        devices.switchTo("B", metadata("humidity"))
        val state = dashboard.awaitState { !it.loadingLayout && it.widgets.singleOrNull()?.widget?.source?.deviceId == "B" }
        assertEquals("humidity", state.widgets.single().widget.source.capabilityId)
        gate.complete(Unit)
        assertEquals("B", dashboard.awaitState { !it.loadingLayout }.widgets.single().widget.source.deviceId)
        assertEquals(old, storage.observe("A").first())
    }

    @Test fun `writes survive navigation and never overwrite switched device while readings continue`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val delegate = createTestDashboardDataStore(worker) { File(temporary.root, "delayed.preferences_pb") }
        val blockedWrites = object : DataStore<Preferences> {
            override val data = delegate.data
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                gate.await()
                return delegate.updateData(transform)
            }
        }
        val storage = DataStoreDashboardLayoutRepository(blockedWrites, worker)
        val devices = Devices(metadata("temperature"))
        val first = vm(devices, storage)
        val id = first.awaitState { !it.loadingLayout && it.widgets.size == 1 }.widgets.single().widget.id
        first.setEditing(true)
        repeat(5) { first.toggleWidth(id) }
        first.configureWidget(id, WidgetType.RANGE, WidgetSettings(300, -10.0, 60.0, 0.0, 40.0, 3, 30))
        first.duplicateWidget(id)
        devices.sensorValues.value = mapOf("temperature" to "33")
        assertEquals("33", first.awaitState { it.isEditing && it.widgets.singleOrNull()?.value == "33" }.widgets.single().value)
        // Clear the originating VM while its application-owned writes are blocked.
        viewModels.clear()
        devices.switchTo("B", metadata("humidity"))
        val second = vm(devices, storage, "second")
        val b = second.awaitState { !it.loadingLayout && it.widgets.singleOrNull()?.widget?.source?.deviceId == "B" }
        gate.complete(Unit)
        withTimeout(5000) { storage.observe("A").first { it?.widgets?.size == 2 } }
        assertEquals(b.widgets.map { it.widget }, second.awaitState { !it.loadingLayout }.widgets.map { it.widget })
        assertNull(storage.observe("B").first())
        devices.switchTo("A", metadata("temperature"))
        val restored = second.awaitState { !it.loadingLayout && it.widgets.size == 2 && it.widgets.first().widget.source.deviceId == "A" }
        assertEquals(id, restored.widgets.first().widget.id)
        assertEquals(2, restored.widgets.first().widget.columnSpan)
        assertTrue(restored.widgets.all { it.widget.type == WidgetType.RANGE && it.widget.settings.precision == 3 })
        assertEquals(restored.widgets[0].widget.settings, restored.widgets[1].widget.settings)
        assertNotEquals(restored.widgets[0].widget.id, restored.widgets[1].widget.id)
    }

    @Test fun `capability changes persist additions preserve missing widgets and reset removes customization`() = runBlocking {
        val storage = repository()
        val devices = Devices(metadata("temperature"))
        val dashboard = vm(devices, storage)
        val id = dashboard.awaitState { !it.loadingLayout && it.widgets.size == 1 }.widgets.single().widget.id
        dashboard.hideWidget(id)
        dashboard.awaitState { it.hiddenWidgets.size == 1 }
        devices.capabilities.value = metadata("humidity")
        val changed = dashboard.awaitState { it.hiddenWidgets.size == 1 && it.widgets.singleOrNull()?.widget?.source?.capabilityId == "humidity" }
        assertNull(changed.hiddenWidgets.single().capability)
        assertEquals(id, changed.hiddenWidgets.single().widget.id)
        val saved = storage.observe("A").first()!!
        assertEquals(2, saved.widgets.size)
        devices.capabilities.value = null
        dashboard.awaitState { it.discovering }
        assertEquals(saved, storage.observe("A").first())
        devices.capabilities.value = metadata("temperature", "humidity")
        assertNotNull(dashboard.awaitState { it.hiddenWidgets.singleOrNull()?.capability != null }.hiddenWidgets.single().capability)
        dashboard.resetLayout()
        val reset = dashboard.awaitState { it.hiddenWidgets.isEmpty() && it.widgets.size == 2 }
        assertEquals(defaultDashboard("A", devices.capabilities.value).widgets, reset.widgets.map { it.widget })
        assertNull(storage.observe("A").first())
    }

    @Test fun `failed save keeps stored layout and live readings usable`() = runBlocking {
        val storage = repository()
        val failing = object : DashboardLayoutRepository by storage {
            override fun update(deviceId: String, transform: (DashboardLayout?) -> DashboardLayout?): Deferred<Unit> =
                CompletableDeferred<Unit>().apply { completeExceptionally(java.io.IOException("Disk full")) }
        }
        val devices = Devices(metadata("temperature"))
        val dashboard = vm(devices, failing)
        val initial = dashboard.awaitState { !it.loadingLayout && it.widgets.size == 1 }
        dashboard.toggleWidth(initial.widgets.single().widget.id)
        dashboard.awaitState { it.persistenceError != null }
        devices.sensorValues.value = mapOf("temperature" to "42")
        assertEquals("42", dashboard.awaitState { it.widgets.singleOrNull()?.value == "42" }.widgets.single().value)
        assertEquals(1, dashboard.dashboard.value.widgets.single().widget.columnSpan)
        devices.switchTo("B", metadata("humidity"))
        assertNull(dashboard.awaitState { !it.loadingLayout && it.widgets.singleOrNull()?.widget?.source?.deviceId == "B" }.persistenceError)
    }


    @Test fun `unexpected disconnect preserves customization and reconnect resolves returning sensor`() = runBlocking {
        val storage = repository("loss")
        val devices = Devices(metadata("temperature"))
        val dashboard = vm(devices, storage)
        val id = dashboard.awaitState { !it.loadingLayout && it.widgets.size == 1 }.widgets.single().widget.id
        dashboard.toggleWidth(id)
        dashboard.addWidget(SensorSource("A", "temperature"))
        dashboard.awaitState { it.widgets.size == 2 && it.widgets.first().widget.columnSpan == 2 }
        val saved = storage.observe("A").first()
        devices.connectionState.value = ConnectionState.ConnectionLost
        devices.capabilities.value = null
        devices.sensorValues.value = emptyMap()
        dashboard.awaitState { it.discovering && it.widgets.size == 2 }
        assertEquals(saved, storage.observe("A").first())
        viewModels.clear()
        devices.switchTo("A", metadata("temperature"))
        val reopened = vm(devices, storage, "reopened")
        devices.sensorValues.value = mapOf("temperature" to "21")
        val restored = reopened.awaitState { it.widgets.size == 2 && it.widgets.all { widget -> widget.value == "21" } }
        assertEquals(saved!!.widgets, restored.widgets.map { it.widget })
    }
    @Test fun `new samples between freshness ticks remain live and visible in chart time`() = runBlocking {
        val devices = Devices(metadata("temperature"))
        val storage = repository("freshness")
        val dashboard = vm(devices, storage)
        dashboard.awaitState { !it.loadingLayout && it.widgets.size == 1 }
        val source = SensorSource("A", "temperature")
        fun publish(time: Long) {
            devices.now = time
            devices.sensorHistory.value = SensorHistorySnapshot("A", 1, true,
                mapOf(source to SensorTimeline(listOf(NumericSample(time, 12.0, 0)), time, 12.0, true)))
            devices.sensorValues.value = mapOf("temperature" to "12")
        }
        publish(1000)
        assertEquals(ReadingFreshness.LIVE, dashboard.awaitState {
            it.widgets.singleOrNull()?.timeline?.lastSuccessfulMs == 1000L
        }.widgets.single().freshness)
        publish(1250) // Same numeric value; a new history event before the next UI tick.
        val reading = dashboard.awaitState { it.widgets.singleOrNull()?.timeline?.lastSuccessfulMs == 1250L }.widgets.single()
        assertEquals(ReadingFreshness.LIVE, reading.freshness)
        assertTrue(reading.nowMs >= 1250)
        assertEquals(1, chartSeries(reading.timeline!!.samples, reading.nowMs, 60, 15000)!!.count)
    }

    @Test fun `rapid live updates do not enqueue layout writes`() = runBlocking {
        val storage = repository("no-sensor-writes")
        var writes = 0
        val counted = object : DashboardLayoutRepository by storage {
            override fun update(deviceId: String, transform: (DashboardLayout?) -> DashboardLayout?): Deferred<Unit> {
                writes++; return storage.update(deviceId, transform)
            }
        }
        val devices = Devices(metadata("temperature"))
        val dashboard = vm(devices, counted)
        dashboard.awaitState { !it.loadingLayout && it.widgets.size == 1 }
        repeat(1000) { devices.sensorValues.value = mapOf("temperature" to it.toString()) }
        dashboard.awaitState { it.widgets.singleOrNull()?.value == "999" }
        assertEquals(0, writes)
        assertNull(storage.observe("A").first())
    }

    @Test fun `v2 UUID layout survives changed address epoch reconnect and manifest`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val m = V2Metadata("00112233-4455-6677-8899-aabbccddeeff", "Board", "0000000000000001",
            listOf(V2Sensor("a", "Alpha", "u"), V2Sensor("b", "Beta", "u")))
        val id = m.dashboardDeviceId()
        val devices = Devices(m.dashboardCapabilities())
        devices.switchTo(id, m.dashboardCapabilities())
        val storage = repository("v2")
        val dashboard = vm(devices, storage)
        val first = dashboard.awaitState { !it.loadingLayout && it.widgets.size == 2 }
        storage.update(id) { it }.await()
        assertNotNull(storage.observe(id).first())
        dashboard.duplicateWidget(first.widgets.first().widget.id)
        dashboard.hideWidget(first.widgets.last().widget.id)
        val saved = withTimeout(5000) { storage.observe(id).first { it?.widgets?.size == 3 && it.widgets.any { w -> w.hidden } } }!!
        devices.disconnect()
        devices.capabilities.value = null
        devices.sensorValues.value = emptyMap()
        viewModels.clear()
        val changed = m.copy(epoch = "0000000000000002", sensors = listOf(m.sensors.first(), V2Sensor("c", "Gamma", "u")))
        devices.switchTo(changed.dashboardDeviceId(), changed.dashboardCapabilities())
        val restored = vm(devices, storage, "reconnected")
        val state = restored.awaitState { !it.loadingLayout && it.widgets.size + it.hiddenWidgets.size == 4 }
        assertTrue((state.widgets + state.hiddenWidgets).all { it.value == null })
        val persisted = withTimeout(5000) { storage.observe(id).first { it?.widgets?.size == 4 } }!!
        assertEquals(saved.widgets, persisted.widgets.take(3))
        assertNull(storage.observe("AA:BB").first())
    }

    private class Devices(metadata: DeviceCapabilities) : DeviceRepository {
        var now = 0L
        override fun monotonicNowMs() = now
        override val connectedDeviceId = MutableStateFlow<String?>("A")
        override val scanState = MutableStateFlow<com.dacodahub.physicallink.domain.model.ScanState>(com.dacodahub.physicallink.domain.model.ScanState.Idle)
        override val devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
        override val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
        override val capabilities = MutableStateFlow<DeviceCapabilities?>(metadata)
        override val sensorHistory = MutableStateFlow(com.dacodahub.physicallink.domain.model.SensorHistorySnapshot())
        override val sensorValues = MutableStateFlow<Map<String, String>>(emptyMap())
        fun switchTo(id: String, metadata: DeviceCapabilities) {
            connectionState.value = ConnectionState.Connecting
            capabilities.value = null
            sensorValues.value = emptyMap()
            connectedDeviceId.value = id
            capabilities.value = metadata
            connectionState.value = ConnectionState.Connected
        }
        override fun startScan() {}
        override fun stopScan() {}
        override fun connect(address: String) {}
        override fun refreshBluetoothState() {}
        override fun disconnect() { connectionState.value = ConnectionState.Disconnected }
    }
}
