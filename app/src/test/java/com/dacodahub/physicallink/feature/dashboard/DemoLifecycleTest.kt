package com.dacodahub.physicallink.feature.dashboard

import androidx.lifecycle.*
import com.dacodahub.physicallink.data.repository.DemoDeviceRepository
import com.dacodahub.physicallink.domain.model.MonotonicClock
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoLifecycleTest {
    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }
    @Test fun `simulation is active only while the dashboard lifecycle is resumed`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val owner = Owner()
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            val demo = DemoDeviceRepository(MonotonicClock { testScheduler.currentTime })
            val job = backgroundScope.launch {
                owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { demo.simulateWhileVisible() }
            }
            runCurrent()
            assertTrue(demo.sensorValues.value.isEmpty())
            owner.lifecycle.currentState = Lifecycle.State.RESUMED; runCurrent()
            assertTrue(demo.sensorHistory.value.active)
            val firstSession = demo.sensorHistory.value.sessionId
            owner.lifecycle.currentState = Lifecycle.State.CREATED; runCurrent()
            assertFalse(demo.sensorHistory.value.active)
            val last = demo.sensorValues.value
            advanceTimeBy(60000); runCurrent()
            assertSame(last, demo.sensorValues.value)
            owner.lifecycle.currentState = Lifecycle.State.RESUMED; runCurrent()
            assertTrue(demo.sensorHistory.value.active)
            assertTrue(demo.sensorHistory.value.sessionId > firstSession)
            owner.lifecycle.currentState = Lifecycle.State.DESTROYED; runCurrent()
            assertFalse(demo.sensorHistory.value.active)
            job.join()
        } finally { Dispatchers.resetMain() }
    }
}
