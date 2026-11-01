package com.dacodahub.physicallink.data.repository

import androidx.datastore.preferences.core.*
import com.dacodahub.physicallink.createTestDashboardDataStore
import com.dacodahub.physicallink.core.designsystem.theme.Appearance
import com.dacodahub.physicallink.core.designsystem.theme.appearanceFromStorage
import com.dacodahub.physicallink.domain.model.*
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder

/** Copies closed real DataStore files to a new app directory; does not emulate an OS transport. */
class BackupRestorePersistenceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `allowlisted files restore identities independent widgets demo and preferences without runtime data`() = runBlocking {
        val sourceFiles = temporary.newFolder("old-phone-files")
        val targetFiles = temporary.newFolder("new-phone-files")
        val sourceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val targetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun file(root: File, name: String) = File(root, "datastore/$name.preferences_pb")
        try {
            val store = createTestDashboardDataStore(sourceScope) { file(sourceFiles, "dashboard_layouts") }
            val layouts = DataStoreDashboardLayoutRepository(store, sourceScope)
            val onboarding = DataStoreOnboardingRepository(store, sourceScope)
            val manifest = SensorManifest("12345678-1234-1234-1234-123456789abc", "Board", "epoch-a",
                listOf(ManifestSensor("channel", "Reading", "u")))
            val deviceId = manifest.dashboardDeviceId()
            val source = SensorSource(deviceId, "channel")
            val first = DashboardWidget("chart-a", WidgetType.CHART, source, columnSpan = 2,
                settings = WidgetSettings(chartMinimum = -2e-6, chartMaximum = 1e300, precision = 6))
            val duplicate = first.copy(id = "chart-b", columnSpan = 1, hidden = true,
                settings = WidgetSettings(chartMinimum = null, chartMaximum = 20.0, precision = 1))
            val gauge = DashboardWidget("gauge", WidgetType.GAUGE, source,
                settings = WidgetSettings(minimum = -10.0, maximum = 100.0, precision = 3))
            val range = gauge.copy(id = "range", type = WidgetType.RANGE,
                settings = gauge.settings.copy(minimum = -20.0, precision = 4))
            val saved = DashboardLayout(listOf(duplicate, range, first, gauge))
            val legacyId = "AA:BB:CC:DD:EE:FF"
            val legacy = DashboardLayout(listOf(first.copy(id = "legacy", source = SensorSource(legacyId, "channel"))))
            val demoId = DemoDeviceRepository.DEVICE_ID
            val demo = defaultDashboard(demoId, DemoDeviceRepository.CAPABILITIES)
            layouts.update(deviceId) { saved }.await()
            layouts.update(legacyId) { legacy }.await()
            layouts.update(demoId) { demo }.await()
            onboarding.dismissIntroduction().await()
            onboarding.markDemoExplored().await()
            val appearance = createTestDashboardDataStore(sourceScope) { file(sourceFiles, "appearance") }
            appearance.edit { it[stringPreferencesKey("appearance_mode")] = Appearance.DARK.name }
            val before = store.data.first()
            assertEquals(setOf("layout:$deviceId", "layout:$legacyId", "layout:$demoId",
                "onboarding:introduction_dismissed:v1", "onboarding:demo_explored:v1"), before.asMap().keys.map { it.name }.toSet())
            val encoded = org.json.JSONObject(before[stringPreferencesKey("layout:$deviceId")]!!)
            assertEquals(setOf("version", "deviceId", "widgets"), encoded.keys().asSequence().toSet())
            for (index in 0 until encoded.getJSONArray("widgets").length()) {
                val widget = encoded.getJSONArray("widgets").getJSONObject(index)
                assertEquals(setOf("id", "type", "deviceId", "capabilityId", "columnSpan", "rowSpan", "hidden", "settings"),
                    widget.keys().asSequence().toSet())
                assertEquals(setOf("chartWindowSeconds", "minimum", "maximum", "lowerThreshold", "upperThreshold",
                    "chartMinimum", "chartMaximum", "precision", "staleAfterSeconds"),
                    widget.getJSONObject("settings").keys().asSequence().toSet())
            }
            sourceScope.coroutineContext.job.cancelAndJoin()

            // Neither temporary siblings nor future diagnostic files are part of the file allowlist.
            File(sourceFiles, "datastore/dashboard_layouts.preferences_pb.tmp").writeText("temporary")
            File(sourceFiles, "diagnostic.log").writeText("diagnostic")
            val rules = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/backup_rules.xml"))
            val includes = rules.getElementsByTagName("include")
            for (index in 0 until includes.length) {
                val attributes = includes.item(index).attributes
                assertEquals("file", attributes.getNamedItem("domain").nodeValue)
                val relative = attributes.getNamedItem("path").nodeValue
                val target = File(targetFiles, relative)
                target.parentFile!!.mkdirs()
                File(sourceFiles, relative).copyTo(target)
            }
            assertEquals(setOf("datastore/dashboard_layouts.preferences_pb", "datastore/appearance.preferences_pb"),
                targetFiles.walkTopDown().filter { it.isFile }.map { it.relativeTo(targetFiles).invariantSeparatorsPath }.toSet())

            val restoredStore = createTestDashboardDataStore(targetScope) { file(targetFiles, "dashboard_layouts") }
            val restored = DataStoreDashboardLayoutRepository(restoredStore, targetScope)
            assertEquals(before, restoredStore.data.first())
            assertEquals(saved, restored.observe(deviceId).first())
            assertEquals(legacy, restored.observe(legacyId).first())
            assertEquals(demo, restored.observe(demoId).first())
            assertNull(restored.observe("v2:another-device").first())
            val newEpoch = manifest.copy(epoch = "epoch-b", device = "Renamed board")
            assertEquals(deviceId, newEpoch.dashboardDeviceId())
            assertEquals(saved, restored.observe(deviceId).first()!!.withDiscoveredSensors(deviceId, newEpoch.dashboardCapabilities()))
            val restoredOnboarding = DataStoreOnboardingRepository(restoredStore, targetScope)
            assertTrue(restoredOnboarding.introductionDismissed.first())
            assertTrue(restoredOnboarding.demoExplored.first())
            val restoredAppearance = createTestDashboardDataStore(targetScope) { file(targetFiles, "appearance") }
            assertEquals(Appearance.DARK, appearanceFromStorage(restoredAppearance.data.first()[stringPreferencesKey("appearance_mode")]))

            // A duplicate edit and a Demo reset must not modify another instance/device after restore.
            restored.update(deviceId) { it!!.configureWidget("chart-a", WidgetType.CHART, first.settings.copy(precision = 2)) }.await()
            assertEquals(duplicate, restored.observe(deviceId).first()!!.widgets.first())
            restored.update(demoId) { null }.await()
            assertNull(restored.observe(demoId).first())
            assertEquals(legacy, restored.observe(legacyId).first())
            assertEquals(4, restored.observe(deviceId).first()!!.widgets.size)
        } finally {
            sourceScope.coroutineContext.job.cancelAndJoin()
            targetScope.coroutineContext.job.cancelAndJoin()
        }
    }
}
