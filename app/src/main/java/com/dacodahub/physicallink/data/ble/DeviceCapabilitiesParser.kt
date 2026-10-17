package com.dacodahub.physicallink.data.ble

import com.dacodahub.physicallink.domain.model.DeviceCapabilities
import com.dacodahub.physicallink.domain.model.DeviceCapability
import org.json.JSONObject

object DeviceCapabilitiesParser {

    fun parse(json: String): DeviceCapabilities {
        val root = JSONObject(json)

        val version = root.getInt("version")
        require(version == 1) {
            "Unsupported capability protocol version: $version"
        }

        val capabilitiesArray = root.getJSONArray("capabilities")

        val capabilities = buildList {
            for (i in 0 until capabilitiesArray.length()) {
                val item = capabilitiesArray.getJSONObject(i)

                add(
                    DeviceCapability(
                        id = item.getString("id"),
                        uuid = item.getString("uuid"),
                        unit = item.getString("unit"),
                        type = item.getString("type")
                    )
                )
            }
        }

        return DeviceCapabilities(
            version = root.getInt("version"),
            device = root.getString("device"),
            capabilities = capabilities
        )
    }
}
