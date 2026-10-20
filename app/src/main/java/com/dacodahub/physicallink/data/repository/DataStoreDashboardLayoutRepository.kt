package com.dacodahub.physicallink.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.dacodahub.physicallink.domain.model.DashboardLayout
import com.dacodahub.physicallink.domain.repository.DashboardLayoutRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.logging.Level
import java.util.logging.Logger

class DataStoreDashboardLayoutRepository(
    private val store: DataStore<Preferences>,
    scope: CoroutineScope
) : DashboardLayoutRepository {
    private val logger = Logger.getLogger("DashboardLayouts")
    private data class Write(
        val deviceId: String,
        val transform: (DashboardLayout?) -> DashboardLayout?,
        val completion: CompletableDeferred<Unit>
    )
    private val writes = Channel<Write>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (write in writes) {
                try {
                    store.edit { preferences ->
                        val key = key(write.deviceId)
                        val current = preferences.asMap()[key]?.let { decode(write.deviceId, it) }
                        val next = write.transform(current)
                        if (next == null) preferences.remove(key)
                        else if (next != current) preferences[key] = DashboardLayoutCodec.encode(write.deviceId, next)
                    }
                    write.completion.complete(Unit)
                } catch (cancelled: CancellationException) {
                    write.completion.cancel(cancelled)
                    throw cancelled
                } catch (error: Exception) {
                    logger.log(Level.WARNING, "Unable to save dashboard layout", error)
                    write.completion.completeExceptionally(error)
                }
            }
        }
    }

    override fun observe(deviceId: String): Flow<DashboardLayout?> = store.data.map { preferences ->
        val key = key(deviceId)
        val raw = preferences.asMap()[key] ?: return@map null
        val layout = decode(deviceId, raw)
        if (layout == null) {
            // Never erase a newer write that raced with recovery of an older bad record.
            store.edit { if (it.asMap()[key] == raw) it.remove(key) }
        }
        layout
    }.distinctUntilChanged()

    override fun update(deviceId: String, transform: (DashboardLayout?) -> DashboardLayout?): Deferred<Unit> {
        require(deviceId.isNotBlank())
        val completion = CompletableDeferred<Unit>()
        val result = writes.trySend(Write(deviceId, transform, completion))
        if (result.isFailure) completion.completeExceptionally(IllegalStateException("Layout writer unavailable"))
        return completion
    }

    private fun key(deviceId: String) = stringPreferencesKey("layout:$deviceId")
    private fun decode(deviceId: String, raw: Any): DashboardLayout? = try {
        require(raw is String) { "Invalid dashboard preference type" }
        DashboardLayoutCodec.decode(deviceId, raw)
    } catch (error: Exception) {
        logger.log(Level.WARNING, "Discarding corrupt or incompatible dashboard record", error)
        null
    }
}
