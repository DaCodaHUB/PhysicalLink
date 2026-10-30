package com.dacodahub.physicallink.core.designsystem.theme

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.emptyPreferences

private val Context.appearanceStore by preferencesDataStore(name = "appearance")
enum class Appearance(val label: String) { SYSTEM("System default"), LIGHT("Light"), DARK("Dark") }
internal fun appearanceFromStorage(value: String?): Appearance = Appearance.entries.firstOrNull { it.name == value } ?: Appearance.SYSTEM
class AppearanceSettings(context: Context) {
    private val store = context.applicationContext.appearanceStore
    private val key = stringPreferencesKey("appearance_mode")
    val appearance = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { appearanceFromStorage(it[key]) }
    suspend fun setAppearance(value: Appearance) { store.edit { it[key] = value.name } }
}
