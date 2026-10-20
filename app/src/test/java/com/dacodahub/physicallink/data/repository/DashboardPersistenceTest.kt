package com.dacodahub.physicallink.data.repository

import androidx.datastore.preferences.core.*
import com.dacodahub.physicallink.createTestDashboardDataStore
import com.dacodahub.physicallink.domain.model.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder

class DashboardPersistenceTest {
    @get:Rule val temporary = TemporaryFolder()
    private val scopes = mutableListOf<CoroutineScope>()
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
    private fun layout(device: String) = DashboardLayout(WidgetType.entries.mapIndexed { i, type ->
        DashboardWidget("widget-$i", type, SensorSource(device, "temperature"), i % 2 + 1, i + 1, i == 1)
    })
    @After fun closeStores() = runBlocking { scopes.forEach { it.coroutineContext.job.cancelAndJoin() } }

    @Test fun `all fields survive disk close and reopen with device isolation and reset`() = runBlocking {
        val file = File(temporary.root, "layouts.preferences_pb")
        val firstScope = scope()
        val first = DataStoreDashboardLayoutRepository(createTestDashboardDataStore(firstScope) { file }, firstScope)
        assertNull(first.observe("A").first())
        first.update("A") { layout("A") }.await()
        first.update("B") { layout("B").copy(widgets = layout("B").widgets.reversed()) }.await()
        firstScope.coroutineContext.job.cancelAndJoin()
        val reopenedScope = scope()
        val reopened = DataStoreDashboardLayoutRepository(createTestDashboardDataStore(reopenedScope) { file }, reopenedScope)
        assertEquals(layout("A"), reopened.observe("A").first())
        assertEquals(layout("B").widgets.reversed(), reopened.observe("B").first()!!.widgets)
        reopened.update("A") { null }.await()
        assertNull(reopened.observe("A").first())
        assertEquals(layout("B").widgets.reversed(), reopened.observe("B").first()!!.widgets)
    }

    @Test fun `corrupt incompatible and foreign records recover without affecting another device`() = runBlocking {
        val worker = scope()
        val store = createTestDashboardDataStore(worker) { File(temporary.root, "records.preferences_pb") }
        val repository = DataStoreDashboardLayoutRepository(store, worker)
        repository.update("B") { layout("B") }.await()
        val invalid = listOf("{broken", "{}",
            DashboardLayoutCodec.encode("A", layout("A")).replace("\"version\":2", "\"version\":99"),
            DashboardLayoutCodec.encode("A", layout("A")).replace("VALUE", "FUTURE"),
            DashboardLayoutCodec.encode("B", layout("B")),
            org.json.JSONObject(DashboardLayoutCodec.encode("A", layout("A"))).put("version", 1.5).toString(),
            org.json.JSONObject(DashboardLayoutCodec.encode("A", layout("A"))).apply {
                getJSONArray("widgets").getJSONObject(0).getJSONObject("settings").put("precision", 1.5)
            }.toString())
        for (record in invalid) {
            store.edit { it[stringPreferencesKey("layout:A")] = record }
            assertNull(repository.observe("A").first())
            assertNull(store.data.first()[stringPreferencesKey("layout:A")])
            assertEquals(layout("B"), repository.observe("B").first())
        }
        store.edit { it[intPreferencesKey("layout:A")] = 123 }
        assertNull(repository.observe("A").first())
        assertFalse(store.data.first().asMap().keys.any { it.name == "layout:A" })
        repository.update("A") { layout("A") }.await()
        assertEquals(layout("A"), repository.observe("A").first())
    }

    @Test fun `whole file corruption is replaced by empty preferences and accepts future saves`() = runBlocking {
        val file = File(temporary.root, "corrupt.preferences_pb")
        file.writeBytes(byteArrayOf(-1, -1, -1, -1))
        val worker = scope()
        val repository = DataStoreDashboardLayoutRepository(createTestDashboardDataStore(worker) { file }, worker)
        assertNull(repository.observe("A").first())
        repository.update("A") { layout("A") }.await()
        assertEquals(layout("A"), repository.observe("A").first())
    }

    @Test fun `rapid queued transforms and reset use latest saved value in submission order`() = runBlocking {
        val worker = scope()
        val repository = DataStoreDashboardLayoutRepository(createTestDashboardDataStore(worker) {
            File(temporary.root, "rapid.preferences_pb")
        }, worker)
        val widget = layout("A").widgets.first()
        repository.update("A") { DashboardLayout(listOf(widget)) }.await()
        val edits = (1..101).map { repository.update("A") { it!!.toggleWidgetWidth(widget.id) } }
        val other = repository.update("B") { layout("B") }
        edits.awaitAll(); other.await()
        assertEquals(2, repository.observe("A").first()!!.widgets.single().columnSpan)
        val reset = repository.update("A") { null }
        val staleReconcile = repository.update("A") { current -> current?.withDiscoveredSensors("A", metadata("new")) }
        reset.await(); staleReconcile.await()
        assertNull(repository.observe("A").first())
        assertEquals(layout("B"), repository.observe("B").first())
    }

    @Test fun `reconciliation keeps missing hidden duplicate and resized widgets and appends new sensors once`() {
        val saved = layout("A").copy(widgets = layout("A").widgets.reversed())
        val reconciled = saved.withDiscoveredSensors("A", metadata("temperature", "new", "new"))
        assertEquals(saved.widgets, reconciled.widgets.take(saved.widgets.size))
        assertEquals("new", reconciled.widgets.last().source.capabilityId)
        assertEquals(reconciled, reconciled.withDiscoveredSensors("A", metadata("new")))
        assertEquals(reconciled, reconciled.withDiscoveredSensors("A", metadata()))
        assertEquals(reconciled, DashboardLayoutCodec.decode("A", DashboardLayoutCodec.encode("A", reconciled)))
    }

    @Test fun `codec rejects duplicate identities and invalid sizes but allows duplicate sensors`() {
        assertEquals(layout("A"), DashboardLayoutCodec.decode("A", DashboardLayoutCodec.encode("A", layout("A"))))
        val widget = layout("A").widgets.first()
        assertThrows(IllegalArgumentException::class.java) {
            DashboardLayoutCodec.encode("A", DashboardLayout(listOf(widget, widget)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            DashboardLayoutCodec.encode("A", DashboardLayout(listOf(widget.copy(columnSpan = 0))))
        }
    }


    @Test fun `explicit selections persist across reopen without changing identity or hidden instances`() = runBlocking {
        val file = File(temporary.root, "selection.preferences_pb")
        val firstScope = scope()
        val first = DataStoreDashboardLayoutRepository(createTestDashboardDataStore(firstScope) { file }, firstScope)
        val original = layout("A")
        first.update("A") { original }.await()
        val writes = (1..20).map { first.update("A") { it!!.resizeWidget("widget-0", 2).setWidgetType("widget-1", WidgetType.VALUE) } }
        writes.awaitAll()
        firstScope.coroutineContext.job.cancelAndJoin()
        val secondScope = scope()
        val reopened = DataStoreDashboardLayoutRepository(createTestDashboardDataStore(secondScope) { file }, secondScope)
        val saved = reopened.observe("A").first()!!
        assertEquals(original.widgets.map { it.id }, saved.widgets.map { it.id })
        assertEquals(original.widgets.map { it.source }, saved.widgets.map { it.source })
        assertEquals(original.widgets.map { it.hidden }, saved.widgets.map { it.hidden })
        assertEquals(2, saved.widgets.first().columnSpan)
        assertEquals(WidgetType.VALUE, saved.widgets[1].type)
    }
    @Test fun `version one migrates on disk preserving all widget configuration and supports version two settings`() = runBlocking {
        val worker = scope()
        val store = createTestDashboardDataStore(worker) { File(temporary.root, "migration.preferences_pb") }
        val key = stringPreferencesKey("layout:A")
        val legacy = """{"version":1,"deviceId":"A","widgets":[{"id":"stable","type":"GAUGE","deviceId":"A","capabilityId":"temperature","columnSpan":2,"rowSpan":3,"hidden":true}]}"""
        store.edit { it[key] = legacy }
        val repository = DataStoreDashboardLayoutRepository(store, worker)
        val migrated = repository.observe("A").first()!!
        val original = DashboardWidget("stable", WidgetType.GAUGE, SensorSource("A", "temperature"), 2, 3, true)
        assertEquals(listOf(original), migrated.widgets)
        assertEquals(2, org.json.JSONObject(store.data.first()[key]!!).getInt("version"))
        val settings = WidgetSettings(900, -10.0, 40.0, 0.0, 35.0, 4, 45)
        repository.update("A") { it!!.configureWidget("stable", WidgetType.RANGE, settings) }.await()
        assertEquals(original.copy(type = WidgetType.RANGE, settings = settings), repository.observe("A").first()!!.widgets.single())
        repository.update("A") { null }.await()
        assertNull(repository.observe("A").first())
    }

    @Test fun `invalid persisted settings recover safely without touching other devices`() = runBlocking {
        val worker = scope()
        val store = createTestDashboardDataStore(worker) { File(temporary.root, "settings-corruption.preferences_pb") }
        val repository = DataStoreDashboardLayoutRepository(store, worker)
        repository.update("B") { layout("B") }.await()
        val encoded = DashboardLayoutCodec.encode("A", layout("A"))
        val broken = org.json.JSONObject(encoded)
        broken.getJSONArray("widgets").getJSONObject(0).getJSONObject("settings").put("precision", 100)
        store.edit { it[stringPreferencesKey("layout:A")] = broken.toString() }
        assertNull(repository.observe("A").first())
        assertEquals(layout("B"), repository.observe("B").first())
    }

    private fun metadata(vararg ids: String) = DeviceCapabilities(1, "Board", ids.map {
        DeviceCapability(it, "uuid-$it", "C", "number")
    })
}
