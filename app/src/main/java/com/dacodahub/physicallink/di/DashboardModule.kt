package com.dacodahub.physicallink.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.dacodahub.physicallink.data.repository.DataStoreOnboardingRepository
import com.dacodahub.physicallink.domain.repository.OnboardingRepository
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
    fun provideDashboardDataStore(@ApplicationContext context: Context): DataStore<Preferences> = createDashboardDataStore(
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    ) { context.preferencesDataStoreFile("dashboard_layouts") }

    @Provides @Singleton
    fun provideDashboardLayouts(store: DataStore<Preferences>): DashboardLayoutRepository =
        DataStoreDashboardLayoutRepository(store, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    @Provides @Singleton
    fun provideOnboarding(store: DataStore<Preferences>): OnboardingRepository =
        DataStoreOnboardingRepository(store, CoroutineScope(SupervisorJob() + Dispatchers.IO))
}
