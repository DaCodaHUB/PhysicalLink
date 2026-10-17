
package com.dacodahub.physicallink.data.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceCapabilitiesParserTest {

    private val validJson = """
        {
            "version": 1,
            "device": "PhysicalLink Soil",
            "capabilities": [
                {
                    "id": "soil",
                    "uuid": "5678",
                    "unit": "raw",
                    "type": "number"
                },
                {
                    "id": "temperature",
                    "uuid": "5679",
                    "unit": "C",
                    "type": "number"
                }
            ]
        }
    """.trimIndent()

    @Test
    fun `parses device capabilities`() {
        val result = DeviceCapabilitiesParser.parse(validJson)

        assertEquals(1, result.version)
        assertEquals("PhysicalLink Soil", result.device)
        assertEquals(2, result.capabilities.size)

        val temperature = result.capabilities[1]

        assertEquals("temperature", temperature.id)
        assertEquals("5679", temperature.uuid)
        assertEquals("C", temperature.unit)
        assertEquals("number", temperature.type)
    }

    @Test
    fun `rejects unsupported protocol versions`() {
        val unsupportedJson =
            validJson.replace("\"version\": 1", "\"version\": 2")

        assertThrows(IllegalArgumentException::class.java) {
            DeviceCapabilitiesParser.parse(unsupportedJson)
        }
    }
}
