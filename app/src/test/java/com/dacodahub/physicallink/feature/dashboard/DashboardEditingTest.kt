package com.dacodahub.physicallink.feature.dashboard

import androidx.lifecycle.ViewModelStore
import com.dacodahub.physicallink.FakeDashboardLayoutRepository
import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.domain.repository.DeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Before
import org.junit.After
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardEditingTest {
    @Before fun setMainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetMainDispatcher() { Dispatchers.resetMain() }
    private val source = SensorSource("board", "temperature")
    private val a = DashboardWidget("a", WidgetType.VALUE, source)
    private val b = a.copy(id = "b", source = source.copy(capabilityId = "humidity"))
    private val c = a.copy(id = "c")
    private val layout = DashboardLayout(listOf(a, b, c))
    private val capabilities = DeviceCapabilities(1, "Board", listOf(
        DeviceCapability("temperature", "uuid", "C", "number"),
        DeviceCapability("humidity", "uuid2", "%", "number")))

    @Test fun `reorder moves in either direction preserving identity source and original layout`() {
        val moved = layout.moveWidget("a", "c")
        assertEquals(listOf(b, c, a), moved.widgets)
        assertEquals(listOf(a, b, c), layout.widgets)
        assertEquals(layout, moved.moveWidget("a", "b"))
        assertEquals(layout, layout.moveWidget("missing", "b"))
        assertEquals(layout, layout.moveWidget("a", "missing"))
        assertEquals(layout, layout.moveWidget("a", "a"))
    }

    @Test fun `accessible moves skip hidden widgets and stop at boundaries`() {
        val hidden = layout.setWidgetHidden("b", true)
        assertEquals(listOf("b", "c", "a"), hidden.moveWidgetBy("a", 1).widgets.map { it.id })
        assertEquals(hidden, hidden.moveWidgetBy("a", -1))
        assertEquals(hidden, hidden.moveWidgetBy("c", 1))
        assertEquals(hidden, hidden.moveWidgetBy("b", 1))
        assertEquals(hidden, hidden.moveWidgetBy("a", 2))
    }

    @Test fun `resize toggles one and two columns without changing source or identity`() {
        assertEquals(a.copy(columnSpan = 2), layout.toggleWidgetWidth("a").widgets.first())
        assertEquals(layout, layout.toggleWidgetWidth("a").toggleWidgetWidth("a"))
        assertEquals(layout, layout.toggleWidgetWidth("missing"))
    }

    @Test fun `hide and restore preserve configuration and placement`() {
        val resized = layout.toggleWidgetWidth("a")
        val hidden = resized.setWidgetHidden("a", true)
        assertEquals(resized.widgets.first().copy(hidden = true), hidden.widgets.first())
        val state = dashboardState("board", capabilities, mapOf("temperature" to "12"), hidden)
        assertEquals(listOf("b", "c"), state.widgets.map { it.widget.id })
        assertEquals("12", state.hiddenWidgets.single().value)
        assertEquals(resized, hidden.setWidgetHidden("a", false))
    }

    @Test fun `duplicate sensors use independent widget identities`() {
        val duplicate = layout.addValueWidget("new", source)
        assertEquals(4, duplicate.widgets.size)
        assertEquals(source, duplicate.widgets.last().source)
        assertEquals(3, duplicate.widgets.count { it.source == source })
        assertEquals(duplicate, duplicate.addValueWidget("new", source))
        assertEquals(layout, layout.addValueWidget("", source))
        val hidden = duplicate.setWidgetHidden("new", true)
        assertFalse(hidden.widgets.first().hidden)
    }

    @Test fun `edited widgets keep live readings and association through capability changes`() {
        val edited = layout.moveWidget("a", "c").toggleWidgetWidth("a").setWidgetHidden("c", true)
        val state = dashboardState("board", capabilities, mapOf("temperature" to "19", "humidity" to "40"), edited)
        assertEquals(listOf("40", "19"), state.widgets.map { it.value })
        assertEquals("19", state.hiddenWidgets.single().value)
        val missing = dashboardState("board", capabilities.copy(capabilities = emptyList()), emptyMap(), edited)
        assertEquals(state.widgets.map { it.widget }, missing.widgets.map { it.widget })
        assertTrue(missing.widgets.all { it.capability == null && it.value == null })
    }

    @Test fun `view model edits retain live updates and reset returns to discovery defaults`() = runBlocking {
        val repository = FakeRepository(capabilities)
        val vm = DashboardViewModel(repository, FakeDashboardLayoutRepository(), androidx.lifecycle.SavedStateHandle())
        val store = ViewModelStore()
        store.put("dashboard", vm)
        suspend fun awaitState(predicate: (DashboardState) -> Boolean) =
            withTimeout(5000) { vm.dashboard.first(predicate) }
        try {
            vm.setEditing(true)
            val initial = awaitState { it.isEditing && it.widgets.size == 2 }
            val id = initial.widgets.first().widget.id
            vm.toggleWidth(id)
            vm.addWidget(source)
            val edited = awaitState { it.widgets.size == 3 && it.widgets.first().widget.columnSpan == 2 }
            assertEquals(3, edited.widgets.map { it.widget.id }.toSet().size)
            repository.sensorValues.value = mapOf("temperature" to "27")
            val live = awaitState { it.widgets.first().value == "27" }
            assertEquals("27", live.widgets.last().value)
            vm.hideWidget(id)
            awaitState { it.hiddenWidgets.size == 1 }
            vm.restoreWidget(id)
            awaitState { it.hiddenWidgets.isEmpty() && it.widgets.size == 3 }
            vm.resetLayout()
            val reset = awaitState { it.widgets.size == 2 && it.widgets.all { widget -> widget.widget.columnSpan == 1 } }
            assertEquals(initial.widgets.map { it.widget }, reset.widgets.map { it.widget })
            assertEquals("27", reset.widgets.first().value)
            vm.setEditing(false)
            assertFalse(awaitState { !it.isEditing }.isEditing)
            vm.addWidget(source.copy(deviceId = "other"))
            vm.addWidget(source.copy(capabilityId = "missing"))
            repository.capabilities.value = capabilities.copy(capabilities = capabilities.capabilities.take(1))
            assertEquals(1, awaitState { it.widgets.size == 1 }.widgets.size)
        } finally { store.clear() }
    }


    @Test fun `explicit width and visualization selections preserve all other saved fields`() {
        val customized = layout.setWidgetHidden("b", true).moveWidget("a", "c")
        val selected = customized.resizeWidget("a", 2).setWidgetType("a", WidgetType.VALUE)
        assertEquals(selected, selected.resizeWidget("a", 2))
        assertEquals(customized.widgets.map { it.id }, selected.widgets.map { it.id })
        assertEquals(customized.widgets.map { it.source }, selected.widgets.map { it.source })
        assertEquals(customized.widgets.map { it.hidden }, selected.widgets.map { it.hidden })
        assertEquals(selected, selected.resizeWidget("a", 0))
        assertEquals(selected, selected.resizeWidget("missing", 2))
    }
    private class FakeRepository(metadata: DeviceCapabilities) : DeviceRepository {
        override val connectedDeviceId = MutableStateFlow<String?>("board")
        override val scanState = MutableStateFlow<com.dacodahub.physicallink.domain.model.ScanState>(com.dacodahub.physicallink.domain.model.ScanState.Idle)
        override val devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
        override val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
        override val capabilities = MutableStateFlow<DeviceCapabilities?>(metadata)
        override val sensorHistory = MutableStateFlow(com.dacodahub.physicallink.domain.model.SensorHistorySnapshot())
        override val sensorValues = MutableStateFlow<Map<String, String>>(emptyMap())
        override fun startScan() {}
        override fun stopScan() {}
        override fun connect(address: String) {}
        override fun refreshBluetoothState() {}
        override fun disconnect() {}
    }
}
