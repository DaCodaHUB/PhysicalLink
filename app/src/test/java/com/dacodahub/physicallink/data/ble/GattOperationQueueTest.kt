package com.dacodahub.physicallink.data.ble

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class GattOperationQueueTest {
    private val uuid = UUID.fromString("00005678-0000-1000-8000-00805f9b34fb")

    @Test fun onlyMatchingReadCompletionStartsDescriptorWrite() {
        val started = mutableListOf<GattOperation>()
        val read = GattOperation.ReadCharacteristic(uuid)
        val notify = GattOperation.EnableNotifications(uuid)
        val queue = GattOperationQueue({ started.add(it); true }, { _, _ -> fail("Unexpected failure") })
        queue.enqueue(listOf(read, notify))
        assertEquals(listOf(read), started)
        queue.complete(notify, true)
        queue.complete(GattOperation.ReadCharacteristic(uuid), true)
        assertSame(read, queue.current)
        queue.complete(read, true)
        assertEquals(listOf(read, notify), started)
        assertSame(notify, queue.current)
        queue.complete(read, true)
        assertSame(notify, queue.current)
        queue.complete(notify, true)
        assertNull(queue.current)
    }

    @Test fun timeoutAbortsPendingWorkAndOldTimeoutCannotAffectNewRequest() {
        val started = mutableListOf<GattOperation>()
        var failures = 0
        val read = GattOperation.ReadCharacteristic(uuid)
        val queue = GattOperationQueue({ started.add(it); true }, { _, _ -> failures++ })
        queue.enqueue(listOf(read, GattOperation.EnableNotifications(uuid)))
        queue.timeout(read)
        assertNull(queue.current)
        assertEquals(1, started.size)
        assertEquals(1, failures)
        val next = GattOperation.ReadCharacteristic(uuid)
        queue.enqueue(listOf(next))
        queue.timeout(read)
        queue.complete(read, true)
        assertSame(next, queue.current)
        assertEquals(1, failures)
    }

    @Test fun rejectedStartAndFailedCallbackAbortPendingWork() {
        for (startAccepted in listOf(false, true)) {
            var starts = 0
            var failures = 0
            val read = GattOperation.ReadCharacteristic(uuid)
            val queue = GattOperationQueue({ starts++; startAccepted }, { _, _ -> failures++ })
            queue.enqueue(listOf(read, GattOperation.EnableNotifications(uuid)))
            if (startAccepted) queue.complete(read, false)
            assertEquals(1, starts)
            assertEquals(1, failures)
            assertNull(queue.current)
        }
    }

    @Test fun clearingSessionDiscardsWorkAndIgnoresLateCompletion() {
        var starts = 0
        val read = GattOperation.ReadCharacteristic(uuid)
        val queue = GattOperationQueue({ starts++; true }, { _, _ -> fail("Unexpected failure") })
        queue.enqueue(listOf(read, GattOperation.EnableNotifications(uuid)))
        queue.clear()
        queue.complete(read, true)
        queue.timeout(read)
        assertNull(queue.current)
        assertEquals(1, starts)
    }
}
