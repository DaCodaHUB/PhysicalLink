package com.dacodahub.physicallink

import com.dacodahub.physicallink.domain.repository.OnboardingRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

class FakeOnboardingRepository : OnboardingRepository {
    override val demoExplored = MutableStateFlow(false)
    override fun markDemoExplored() = CompletableDeferred(Unit).also { demoExplored.value = true }
    override val introductionDismissed = MutableStateFlow(false)
    override fun dismissIntroduction() = CompletableDeferred(Unit).also { introductionDismissed.value = true }
}
