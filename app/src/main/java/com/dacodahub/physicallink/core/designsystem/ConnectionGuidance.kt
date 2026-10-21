package com.dacodahub.physicallink.core.designsystem

import com.dacodahub.physicallink.domain.model.ConnectionState

internal fun isCompatibilityFailure(state: ConnectionState): Boolean {
    val message = (state as? ConnectionState.Error)?.message?.lowercase() ?: return false
    return listOf("capability", "service not found", "characteristic", "descriptor", "unsupported protocol").any { it in message }
}
internal fun connectionHelp(state: ConnectionState): String = when {
    state == ConnectionState.ConnectionLost -> "Check your project's power and range, then select it to reconnect. Your dashboard layout is saved."
    (state as? ConnectionState.Error)?.message?.contains("permission", true) == true -> "Allow Nearby devices permission in app settings, then try again."
    (state as? ConnectionState.Error)?.message?.contains("disabled", true) == true -> "Turn on Bluetooth, then try connecting again."
    (state as? ConnectionState.Error)?.message?.contains("unavailable", true) == true -> "Bluetooth is unavailable on this Android device. Try Demo, or use a device with BLE support."
    (state as? ConnectionState.Error)?.message?.contains("timed out", true) == true -> "The device did not finish connecting or sensor setup in time. Check power and range, close other BLE connections, then retry."
    isCompatibilityFailure(state) -> "Use matching PhysicalLink firmware, check sensor initialization, restart the board, then reconnect. A matching advertised name alone does not guarantee compatibility. See Device details for the diagnostic."
    else -> "Check device power and Bluetooth, move your project closer, then try again. Diagnostics are in Details."
}
internal fun scanHelp(message: String): String = when {
    message.contains("permission", true) -> "Allow Nearby devices (or Location on Android 11 or earlier) in app settings, then scan again."
    message.contains("disabled", true) -> "Turn on Bluetooth, then scan again."
    message.contains("unavailable", true) -> "Bluetooth is unavailable. Check that Bluetooth is enabled, then try again."
    else -> "Stop other Bluetooth scans, wait a moment, then try again. Check the board uses compatible firmware and the expected advertised name; see Device details."
}

internal fun connectionFailureTitle(state: ConnectionState): String {
    val message = (state as? ConnectionState.Error)?.message.orEmpty()
    return when {
        message.contains("permission", true) -> "Bluetooth permission needed"
        message.contains("disabled", true) -> "Bluetooth is disabled"
        message.contains("unavailable", true) -> "Bluetooth unavailable"
        message.contains("timed out", true) -> "Connection timed out"
        isCompatibilityFailure(state) -> "Firmware compatibility issue"
        message.contains("before connection completed", true) -> "Device unavailable"
        else -> "Could not connect"
    }
}
