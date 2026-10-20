package com.dacodahub.physicallink.feature.dashboard

import com.dacodahub.physicallink.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class DashboardStateTest {
    private val temperature = DeviceCapability("temperature", "uuid", "C", "number")
    private fun metadata(vararg sensors: DeviceCapability) = DeviceCapabilities(1, "Board", sensors.toList())

    @Test fun `default generation handles empty unknown invalid and duplicate sensors`() {
        assertTrue(defaultDashboard(null, metadata(temperature)).widgets.isEmpty())
        assertTrue(defaultDashboard("board", null).widgets.isEmpty())
        assertTrue(defaultDashboard("board", metadata()).widgets.isEmpty())
        val layout = defaultDashboard("board", metadata(temperature, temperature,
            temperature.copy(id = ""), temperature.copy(id = "bad", uuid = ""),
            temperature.copy(id = "badType", type = "")))
        assertEquals(1, layout.widgets.size)
        assertEquals(SensorSource("board", "temperature"), layout.widgets.single().source)
        assertEquals(WidgetType.VALUE, layout.widgets.single().type)
    }

    @Test fun `default identities survive reorder metadata updates and distinguish devices`() {
        val second = temperature.copy(id = "humidity")
        val original = defaultDashboard("a", metadata(temperature, second))
        val reordered = defaultDashboard("a", metadata(second, temperature.copy(unit = "F")))
        assertEquals(original.widgets.map { it.id }.toSet(), reordered.widgets.map { it.id }.toSet())
        assertNotEquals(original.widgets.first().id, defaultDashboard("b", metadata(temperature)).widgets.first().id)
    }

    @Test fun `configuration allows several instances per sensor and all modeled types`() {
        val source = SensorSource("a", temperature.id)
        val layout = DashboardLayout(WidgetType.entries.map { DashboardWidget(it.name, it, source) })
        val state = dashboardState("a", metadata(temperature), mapOf("temperature" to "12"), layout)
        assertEquals(3, state.widgets.size)
        assertTrue(state.widgets.all { it.value == "12" && it.capability == temperature })
        assertEquals("13", dashboardState("a", metadata(temperature), mapOf("temperature" to "13"), layout).widgets.first().value)
    }

    @Test fun `capability changes refresh defaults and preserve configured missing widgets`() {
        val initial = dashboardState("a", metadata(temperature), mapOf("temperature" to "12"), null)
        assertFalse(initial.discovering)
        assertTrue(dashboardState("a", metadata(), emptyMap(), null).widgets.isEmpty())
        val layout = DashboardLayout(initial.widgets.map { it.widget })
        val removed = dashboardState("a", metadata(), mapOf("temperature" to "stale"), layout)
        assertNull(removed.widgets.single().capability)
        assertNull(removed.widgets.single().value)
        assertNull(dashboardState("b", metadata(temperature), mapOf("temperature" to "99"), layout).widgets.single().value)
        assertNull(dashboardState("a", null, mapOf("temperature" to "99"), layout).widgets.single().value)
    }

    @Test fun `duplicate widget ids are safe but duplicate sources remain distinct`() {
        val widget = DashboardWidget("one", WidgetType.VALUE, SensorSource("a", temperature.id))
        val state = dashboardState("a", metadata(temperature, temperature.copy(unit = "F")), emptyMap(),
            DashboardLayout(listOf(widget, widget, widget.copy(id = "two"), widget.copy(id = ""))))
        assertEquals(listOf("one", "two"), state.widgets.map { it.widget.id })
        assertTrue(state.widgets.all { it.capability == temperature && it.value == null })
    }
}
