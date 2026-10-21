package com.dacodahub.physicallink.feature

import androidx.lifecycle.ViewModelStore
import com.dacodahub.physicallink.FakeOnboardingRepository
import com.dacodahub.physicallink.data.repository.DemoDeviceRepository
import com.dacodahub.physicallink.domain.model.MonotonicClock
import com.dacodahub.physicallink.domain.repository.*
import com.dacodahub.physicallink.feature.devices.DevicesViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @Test fun `first run dismiss and recreation are passive and normal actions stay independent`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var scans = 0
            val device = object : DeviceRepository by DemoDeviceRepository(MonotonicClock { 0 }) {
                override fun startScan() { scans++ }
            }
            val preferences = FakeOnboardingRepository()
            val first = DevicesViewModel(device, preferences); store.put("first", first)
            runCurrent()
            assertTrue(first.showIntroduction.value)
            assertFalse(first.demoExplored.value)
            first.markDemoExplored(); runCurrent()
            assertTrue(first.demoExplored.value)
            assertTrue(first.showIntroduction.value)
            assertEquals(0, scans)
            first.startScan(); assertEquals(1, scans)
            first.dismissIntroduction(); runCurrent()
            assertFalse(first.showIntroduction.value)
            store.clear(); runCurrent()
            val reopened = DevicesViewModel(device, preferences); store.put("reopened", reopened)
            runCurrent()
            assertFalse(reopened.showIntroduction.value)
            assertTrue(reopened.demoExplored.value)
            assertEquals(1, scans)
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }
    @Test fun `onboarding preference failures never gate device actions`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var scans = 0
            val device = object : DeviceRepository by DemoDeviceRepository(MonotonicClock { 0 }) {
                override fun startScan() { scans++ }
            }
            val broken = object : OnboardingRepository {
                override val demoExplored = flowOf(false)
                override fun markDemoExplored() = CompletableDeferred(Unit)
                override val introductionDismissed = flow<Boolean> { throw java.io.IOException("read failed") }
                override fun dismissIntroduction() = CompletableDeferred<Unit>().apply { completeExceptionally(java.io.IOException("write failed")) }
            }
            val vm = DevicesViewModel(device, broken); store.put("vm", vm)
            runCurrent()
            assertFalse(vm.showIntroduction.value)
            vm.dismissIntroduction(); runCurrent()
            vm.startScan(); assertEquals(1, scans)
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }
}
