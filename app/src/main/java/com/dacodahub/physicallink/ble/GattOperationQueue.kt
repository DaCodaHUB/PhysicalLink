package com.dacodahub.physicallink.ble

import java.util.ArrayDeque

/** Single-flight queue. All calls must use the owner's execution context. */
class GattOperationQueue(
    private val execute: (GattOperation) -> Boolean,
    private val onFailure: (GattOperation, String) -> Unit,
    private val onIdle: () -> Unit = {}
) {
    private val pending = ArrayDeque<GattOperation>()

    var current: GattOperation? = null
        private set

    fun enqueue(operations: List<GattOperation>) {
        pending.addAll(operations)
        executeNext()
    }

    fun complete(operation: GattOperation, success: Boolean) {
        if (current !== operation) return
        if (!success) {
            fail(operation, "GATT operation failed")
            return
        }
        current = null
        executeNext()
    }

    fun timeout(operation: GattOperation) {
        if (current !== operation) return
        // Android cannot cancel a single GATT request. The owner must end the
        // session before accepting more work; never advance after a timeout.
        fail(operation, "GATT operation timed out")
    }

    fun clear() {
        pending.clear()
        current = null
    }

    private fun fail(operation: GattOperation, reason: String) {
        clear()
        onFailure(operation, reason)
    }

    private fun executeNext() {
        if (current != null) return
        val operation = pending.pollFirst()
        if (operation == null) {
            onIdle()
            return
        }
        current = operation
        if (!execute(operation)) {
            fail(operation, "Could not start GATT operation")
        }
    }
}
