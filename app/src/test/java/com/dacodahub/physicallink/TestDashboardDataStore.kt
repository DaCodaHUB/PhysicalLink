package com.dacodahub.physicallink

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.*
import com.dacodahub.physicallink.data.repository.dashboardCorruptionHandler
import java.io.File
import kotlinx.coroutines.CoroutineScope
import okio.FileSystem
import okio.Path.Companion.toPath

/** Android's Build.VERSION is stubbed in JVM tests, selecting File.renameTo on Windows.
 * Use DataStore's real portable storage adapter for atomic replacements in disk tests. */
fun createTestDashboardDataStore(scope: CoroutineScope, file: () -> File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file().absolutePath.toPath() },
        corruptionHandler = dashboardCorruptionHandler(), scope = scope
    )
