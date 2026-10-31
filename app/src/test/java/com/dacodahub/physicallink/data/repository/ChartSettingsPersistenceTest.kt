package com.dacodahub.physicallink.data.repository

import com.dacodahub.physicallink.createTestDashboardDataStore
import com.dacodahub.physicallink.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder

class ChartSettingsPersistenceTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun `per instance bounds survive disk reopen and metadata reconciliation without changing layout`() = runBlocking {
        val source = SensorSource("v2:stable-device", "channel")
        val first = DashboardWidget("chart-a", WidgetType.CHART, source, columnSpan = 2,
            settings = WidgetSettings(chartMinimum = -5.0, precision = 4))
        val second = first.copy(id = "chart-b", columnSpan = 1, hidden = true,
            settings = first.settings.copy(chartMinimum = null, chartMaximum = 12.0))
        val gauge = DashboardWidget("gauge", WidgetType.GAUGE, source,
            settings = WidgetSettings(minimum = -1e300, maximum = 1e300))
        val layout = DashboardLayout(listOf(second, gauge, first))
        val file = File(temporary.root, "chart-settings.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = DataStoreDashboardLayoutRepository(createTestDashboardDataStore(firstScope) { file }, firstScope)
            repository.update(source.deviceId) { layout }.await()
            firstScope.coroutineContext.job.cancelAndJoin()
            val restored = DataStoreDashboardLayoutRepository(createTestDashboardDataStore(secondScope) { file }, secondScope).observe(source.deviceId).first()!!
            assertEquals(layout, restored)
            assertEquals(layout, restored.withDiscoveredSensors(source.deviceId, DeviceCapabilities(2, "Reconnected", listOf(DeviceCapability(source.capabilityId, "v2:channel", "u", "number")))))
            assertEquals(-5.0, restored.widgets.last().settings.chartMinimum!!, 0.0)
            assertEquals(12.0, restored.widgets.first().settings.chartMaximum!!, 0.0)
        } finally { firstScope.coroutineContext.job.cancelAndJoin(); secondScope.coroutineContext.job.cancelAndJoin() }
    }
    @Test fun `existing v2 layouts without new fields keep Auto axes and existing identities`() {
        val source = SensorSource("board", "channel")
        val layout = DashboardLayout(listOf(DashboardWidget("id", WidgetType.CHART, source, columnSpan = 1)))
        val old = JSONObject(DashboardLayoutCodec.encode(source.deviceId, layout))
        val settings = old.getJSONArray("widgets").getJSONObject(0).getJSONObject("settings")
        settings.remove("chartMinimum"); settings.remove("chartMaximum")
        assertEquals(layout, DashboardLayoutCodec.decode(source.deviceId, old.toString()))
    }
}
