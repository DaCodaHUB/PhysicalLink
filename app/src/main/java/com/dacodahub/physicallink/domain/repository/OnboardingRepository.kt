package com.dacodahub.physicallink.domain.repository

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow

interface OnboardingRepository {
    val introductionDismissed: Flow<Boolean>
    /** Application-owned save: navigation must not cancel an accepted dismissal. */
    fun dismissIntroduction(): Deferred<Unit>
}
