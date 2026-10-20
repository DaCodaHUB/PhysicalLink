package com.dacodahub.physicallink.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.dacodahub.physicallink.domain.repository.OnboardingRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class DataStoreOnboardingRepository(private val store: DataStore<Preferences>, private val scope: CoroutineScope) : OnboardingRepository {
    private val dismissed = booleanPreferencesKey("onboarding:introduction_dismissed:v1")
    override val introductionDismissed = store.data.map { it[dismissed] ?: false }.distinctUntilChanged()
    override fun dismissIntroduction(): Deferred<Unit> = scope.async { store.edit { it[dismissed] = true }; Unit }
}
