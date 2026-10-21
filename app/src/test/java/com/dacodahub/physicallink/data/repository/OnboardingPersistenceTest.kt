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

class OnboardingPersistenceTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun `first run dismissal survives reopen and does not modify demo or hardware layouts`() = runBlocking {
        val file = File(temporary.root, "introduction.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val firstStore = createTestDashboardDataStore(firstScope) { file }
            val first = DataStoreOnboardingRepository(firstStore, firstScope)
            val layouts = DataStoreDashboardLayoutRepository(firstStore, firstScope)
            assertFalse(first.introductionDismissed.first())
            assertFalse(first.demoExplored.first())
            first.markDemoExplored().await()
            first.markDemoExplored().await()
            assertTrue(first.demoExplored.first())
            assertFalse(first.introductionDismissed.first())
            val demoId = DemoDeviceRepository.DEVICE_ID
            val demo = defaultDashboard(demoId, DemoDeviceRepository.CAPABILITIES)
            val real = defaultDashboard("AA:BB:CC:DD:EE:FF", DemoDeviceRepository.CAPABILITIES)
            layouts.update(demoId) { demo }.await()
            layouts.update("AA:BB:CC:DD:EE:FF") { real }.await()
            first.dismissIntroduction().await()
            first.dismissIntroduction().await()
            assertTrue(first.introductionDismissed.first())
            assertEquals(demo, layouts.observe(demoId).first())
            firstScope.coroutineContext.job.cancelAndJoin()
            val secondStore = createTestDashboardDataStore(secondScope) { file }
            val reopened = DataStoreOnboardingRepository(secondStore, secondScope)
            val reopenedLayouts = DataStoreDashboardLayoutRepository(secondStore, secondScope)
            assertTrue(reopened.introductionDismissed.first())
            assertTrue(reopened.demoExplored.first())
            assertEquals(demo, reopenedLayouts.observe(demoId).first())
            assertEquals(real, reopenedLayouts.observe("AA:BB:CC:DD:EE:FF").first())
            reopenedLayouts.update(demoId) { null }.await()
            assertTrue(reopened.introductionDismissed.first())
            assertTrue(reopened.demoExplored.first())
        } finally {
            firstScope.coroutineContext.job.cancelAndJoin()
            secondScope.coroutineContext.job.cancelAndJoin()
        }
    }
}
