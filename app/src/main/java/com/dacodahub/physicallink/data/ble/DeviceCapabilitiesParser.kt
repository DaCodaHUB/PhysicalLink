package com.dacodahub.physicallink.data.ble

import com.dacodahub.physicallink.domain.model.DeviceCapabilities
import com.dacodahub.physicallink.domain.model.DeviceCapability
import org.json.JSONObject

object DeviceCapabilitiesParser {

    fun parse(json: String): DeviceCapabilities {
        val root = JSONObject(json)

        val version = root.get("version")
        require(version == 1) {
            "Unsupported capability protocol version: $version"
        }

        val capabilitiesArray = root.getJSONArray("capabilities")

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
