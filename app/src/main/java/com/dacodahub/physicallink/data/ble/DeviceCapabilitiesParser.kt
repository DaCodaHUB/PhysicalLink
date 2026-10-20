package com.dacodahub.physicallink.data.ble

import com.dacodahub.physicallink.domain.model.DeviceCapabilities
import com.dacodahub.physicallink.domain.model.DeviceCapability
import org.json.JSONObject
import org.json.JSONTokener

object DeviceCapabilitiesParser {
    // Resource limits for untrusted peripheral metadata, not a new wire format.
    const val MAX_JSON_LENGTH = 16_384
    const val MAX_CAPABILITIES = 64
    private fun checkBounds(json: String) {
        require(json.length <= MAX_JSON_LENGTH) { "Capability payload is too large" }
        var depth = 0; var quoted = false; var escaped = false
        json.forEach { char ->
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 16) { "Capability JSON is too deeply nested" } }
                '}', ']' -> { depth--; require(depth >= 0) { "Invalid capability JSON nesting" } }
                '/', '\'' -> throw IllegalArgumentException("JSON comments and single-quoted strings are not supported")
            }
        }
        require(depth == 0 && !quoted) { "Incomplete capability JSON" }
    }

    fun parse(json: String): DeviceCapabilities {
        checkBounds(json)
        val tokens = JSONTokener(json)
        val root = tokens.nextValue()
        require(root is JSONObject) { "Capability description must be an object" }
        require(tokens.nextClean() == 0.toChar()) { "Trailing capability data" }

        val version = root.get("version")
        require(version == 1) {
            "Unsupported capability protocol version: $version"
        }

        val capabilitiesArray = root.getJSONArray("capabilities")

        require(capabilitiesArray.length() <= MAX_CAPABILITIES) { "Too many sensor capabilities" }
        val capabilities = buildList {
            for (i in 0 until capabilitiesArray.length()) {
                val item = capabilitiesArray.getJSONObject(i)

                add(
                    DeviceCapability(
                        id = item.string("id"),
                        uuid = item.string("uuid"),
                        unit = item.string("unit"),
                        type = item.string("type")
                    )
                )
            }
        }

        require(capabilities.all { it.id.isNotBlank() && it.type.isNotBlank() }) { "Blank sensor ID or type" }
        require(capabilities.distinctBy { it.id }.size == capabilities.size) { "Duplicate sensor IDs" }
        val uuids = capabilities.map { capabilityUuid(it.uuid) }
        require(uuids.distinct().size == uuids.size) { "Duplicate sensor UUIDs" }
        require(capabilityUuid("5670") !in uuids) { "Capabilities characteristic cannot be a sensor" }

        return DeviceCapabilities(
            version = 1,
            device = root.string("device"),
            capabilities = capabilities
        )
    }
    private fun JSONObject.string(key: String): String = get(key).let {
        require(it is String) { "$key must be a string" }
        it
    }
}
