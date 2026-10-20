package com.dacodahub.physicallink.data.repository

import com.dacodahub.physicallink.createTestDashboardDataStore
import com.dacodahub.physicallink.domain.model.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DemoPersistenceTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun `demo layouts and settings survive disk recreation and reset cannot affect real device`() = runBlocking {
        val file = File(temporary.root, "demo.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val first = DataStoreDashboardLayoutRepository(createTestDashboardDataStore(firstScope) { file }, firstScope)
            val demoId = DemoDeviceRepository.DEVICE_ID
            val original = defaultDashboard(demoId, DemoDeviceRepository.CAPABILITIES)
            val id = original.widgets.first().id
            val settings = WidgetSettings(900, 18.0, 30.0, precision = 4)
            val customized = original.configureWidget(id, WidgetType.CHART, settings).resizeWidget(id, 2)
                .duplicateWidget(id, "extra").setWidgetHidden(id, true).moveWidgetBy("extra", -1)
            val realId = "AA:BB:CC:DD:EE:FF"
            val real = defaultDashboard(realId, DemoDeviceRepository.CAPABILITIES)
            first.update(demoId) { customized }.await()
            first.update(realId) { real }.await()
            firstScope.coroutineContext.job.cancelAndJoin()
            val reopened = DataStoreDashboardLayoutRepository(createTestDashboardDataStore(secondScope) { file }, secondScope)
            assertEquals(customized, reopened.observe(demoId).first())
            assertEquals(real, reopened.observe(realId).first())
            reopened.update(demoId) { null }.await()
            assertNull(reopened.observe(demoId).first())
            assertEquals(real, reopened.observe(realId).first())
            assertEquals(customized, DashboardLayoutCodec.decode(demoId, DashboardLayoutCodec.encode(demoId, customized)))
        } finally {
            firstScope.coroutineContext.job.cancelAndJoin()
            secondScope.coroutineContext.job.cancelAndJoin()
        }
    }
}
