
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
    @Test fun `rejects malformed missing and incorrectly typed fields`() {
        for (json in listOf("{bad", "{}", validJson.replace("\"id\": \"soil\"", "\"id\": 123"))) {
            assertThrows(Exception::class.java) { DeviceCapabilitiesParser.parse(json) }
        }
    }

    @Test fun `rejects invalid UUIDs duplicate IDs and duplicate canonical UUIDs`() {
        for (json in listOf(validJson.replace("5678", "bad"),
            validJson.replace("temperature", "soil"), validJson.replace("5679", "5678"),
            validJson.replace("5679", "00005678-0000-1000-8000-00805f9b34fb"),
            validJson.replace("5678", "5670"))) {
            assertThrows(IllegalArgumentException::class.java) { DeviceCapabilitiesParser.parse(json) }
        }
    }

    @Test fun `canonical UUID and unknown nonblank types are safe opaque metadata`() {
        val parsed = DeviceCapabilitiesParser.parse(validJson.replace("5678", "00005678-0000-1000-8000-00805f9b34fb")
            .replace("number", "future-type"))
        assertEquals(capabilityUuid("5678"), capabilityUuid(parsed.capabilities.first().uuid))
        assertEquals("future-type", parsed.capabilities.first().type)
    }

    @Test fun `rejects blank ids types and nonintegral versions without coercion`() {
        for (json in listOf(validJson.replace("\"soil\"", "\" \""),
            validJson.replace("\"number\"", "\"\""), validJson.replace("\"version\": 1", "\"version\": 1.5"))) {
            assertThrows(IllegalArgumentException::class.java) { DeviceCapabilitiesParser.parse(json) }
        }
    }

    @Test fun `untrusted metadata rejects trailing content oversized and deeply nested payloads`() {
        val valid = """{"version":1,"device":"Board","capabilities":[]}"""
        listOf(valid + " garbage", " ".repeat(20000) + valid,
            "[".repeat(1000) + "0" + "]".repeat(1000)).forEach { payload ->
            assertThrows(Exception::class.java) { DeviceCapabilitiesParser.parse(payload) }
        }
    }

    @Test fun `capability count and JSON comments cannot bypass resource bounds`() {
        val sensors = (0..64).joinToString(",") { """{"id":"sensor$it","uuid":"${(4096 + it).toString(16)}","unit":"raw","type":"number"}""" }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceCapabilitiesParser.parse("""{"version":1,"device":"Board","capabilities":[$sensors]}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceCapabilitiesParser.parse("""{/* ignored */"version":1,"device":"Board","capabilities":[]}""")
        }
        val quoted = """{"version":1,"device":"Board { [ / \" quoted","capabilities":[]}"""
        assertEquals("Board { [ / \" quoted", DeviceCapabilitiesParser.parse(quoted).device)
    }

}
