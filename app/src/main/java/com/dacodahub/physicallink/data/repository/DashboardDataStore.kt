package com.dacodahub.physicallink.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineScope

fun dashboardCorruptionHandler() = ReplaceFileCorruptionHandler<Preferences> {
    Logger.getLogger("DashboardLayouts").log(Level.WARNING, "Recovering corrupt dashboard DataStore", it)
    emptyPreferences()
}

/** Called once by the application-scoped Hilt provider. */
fun createDashboardDataStore(scope: CoroutineScope, file: () -> File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = dashboardCorruptionHandler(), scope = scope, produceFile = file
    )
