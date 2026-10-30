package com.dacodahub.physicallink.feature

import com.dacodahub.physicallink.core.designsystem.*
import com.dacodahub.physicallink.domain.model.ConnectionState
import org.junit.Assert.*
import org.junit.Test

class ConnectionGuidanceTest {
    @Test fun `connection failures have distinct actionable classifications`() {
        val cases = listOf(
            "Bluetooth unavailable" to "Bluetooth unavailable",
            "Bluetooth is disabled" to "Bluetooth is disabled",
            "Bluetooth permission denied" to "Bluetooth permission needed",
            "Service discovery timed out" to "Connection timed out",
            "Invalid capability description" to "Firmware compatibility issue",
            "Invalid sensor metadata: bad manifest" to "Firmware compatibility issue",
            "Invalid PhysicalLink service" to "Firmware compatibility issue",
            "Device disconnected before connection completed" to "Device unavailable"
        )
        cases.forEach { (error, title) ->
            val state = ConnectionState.Error(error)
            assertEquals(title, connectionFailureTitle(state))
            assertTrue(connectionHelp(state).isNotBlank())
        }
        assertNotEquals(connectionLabel(ConnectionState.Disconnected), connectionLabel(ConnectionState.ConnectionLost))
    }
    @Test fun `synchronization errors explain recovery without exposing wire details`() {
        val raw = "State synchronization failed: Capture deadline exceeded"
        val help = connectionHelp(ConnectionState.Error(raw))
        assertTrue(help.contains("reconnect"))
        assertTrue(help.contains("synchronized"))
        assertFalse(help.contains("Capture deadline"))
    }

}
