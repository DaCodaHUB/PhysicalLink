package com.dacodahub.physicallink.data.ble

import java.util.UUID

/** The protocol uses four hex digits; canonical 128-bit UUIDs are also unambiguous. */
fun capabilityUuid(value: String): UUID = when {
    value.matches(Regex("[0-9a-fA-F]{4}")) -> UUID.fromString("0000$value-0000-1000-8000-00805f9b34fb")
    value.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) -> UUID.fromString(value)
    else -> throw IllegalArgumentException("Invalid sensor UUID")
}
