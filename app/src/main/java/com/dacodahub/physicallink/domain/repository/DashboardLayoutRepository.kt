package com.dacodahub.physicallink.domain.repository

import com.dacodahub.physicallink.domain.model.DashboardLayout
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow

interface DashboardLayoutRepository {
    /** Null means no saved customization. Live readings are never stored here. */
    fun observe(deviceId: String): Flow<DashboardLayout?>

    /** Ordered, atomic transformation of the latest saved value. Null removes customization.
     * Accepted writes belong to the application, independent of screen lifetime. */
    fun update(deviceId: String, transform: (DashboardLayout?) -> DashboardLayout?): Deferred<Unit>
}
