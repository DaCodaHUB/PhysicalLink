package com.dacodahub.physicallink.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import com.dacodahub.physicallink.data.repository.DataStoreDashboardLayoutRepository
import com.dacodahub.physicallink.data.repository.createDashboardDataStore
import com.dacodahub.physicallink.domain.repository.DashboardLayoutRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.*

@Module
@InstallIn(SingletonComponent::class)
object DashboardModule {
    @Provides @Singleton
    fun provideDashboardLayouts(@ApplicationContext context: Context): DashboardLayoutRepository {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return DataStoreDashboardLayoutRepository(createDashboardDataStore(scope) {
            context.preferencesDataStoreFile("dashboard_layouts")
        }, scope)
    }
}

