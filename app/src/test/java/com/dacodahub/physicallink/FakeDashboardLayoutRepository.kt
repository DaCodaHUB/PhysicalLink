package com.dacodahub.physicallink

import com.dacodahub.physicallink.domain.model.DashboardLayout
import com.dacodahub.physicallink.domain.repository.DashboardLayoutRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*

class FakeDashboardLayoutRepository : DashboardLayoutRepository {
    private val saved = MutableStateFlow<Map<String, DashboardLayout>>(emptyMap())
    override fun observe(deviceId: String): Flow<DashboardLayout?> = saved.map { it[deviceId] }.distinctUntilChanged()
    override fun update(deviceId: String, transform: (DashboardLayout?) -> DashboardLayout?) = CompletableDeferred(Unit).also {
        saved.update { layouts -> transform(layouts[deviceId])?.let { next -> layouts + (deviceId to next) } ?: (layouts - deviceId) }
    }
}
