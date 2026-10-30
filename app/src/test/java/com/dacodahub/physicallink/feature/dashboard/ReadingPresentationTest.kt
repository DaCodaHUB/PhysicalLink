package com.dacodahub.physicallink.feature.dashboard

import com.dacodahub.physicallink.data.ble.MetadataParser
import com.dacodahub.physicallink.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ReadingPresentationTest {
    private fun state(raw: String, freshness: ReadingFreshness = ReadingFreshness.LIVE) = DashboardWidgetState(
        DashboardWidget("id", WidgetType.VALUE, SensorSource("board", "channel")),
        DeviceCapability("channel", "fixture", "widgets/(m\u00B2\u00B7s)", "number", "Canal \u0394 \u6E2C\u5B9A"), raw,
        SensorTimeline(lastSuccessfulMs = 0, latestNumeric = raw.toDoubleOrNull(), latestValid = true), freshness, 20000)
    @Test fun `raw UTF8 metadata preserves codepoints through parsing and unit formatting`() {
        val name = "Canal \u0394 \u6E2C\u5B9A \uD83D\uDD27"
        val unit = "widgets/(m\u00B2\u00B7s)"
        val json = """{"version":2,"deviceId":"12345678-1234-1234-1234-123456789abc","device":"Project","epoch":"0000000000000001","sensors":[{"id":"channel","name":"$name","unit":"$unit"}]}"""
        val parsed = MetadataParser.parse(json.toByteArray(Charsets.UTF_8)).sensors.single()
        assertEquals(name, parsed.name)
        assertEquals(unit, parsed.unit)
        assertEquals(listOf(0x6d, 0xb2, 0xb7, 0x73), parsed.unit.substring(9, 13).map { it.code })
        assertEquals(unit, sensorUnit(parsed.unit))
    }
    @Test fun `finite negative tiny and huge values retain meaning`() {
        for (raw in listOf("-12.34", "-0.000000084", "0.000000084", "1.7976931348623157E308", "12345678901234567890")) {
            val item = state(raw)
            assertEquals(raw, readingForDisplay(item))
            assertEquals(ReadingFreshness.LIVE, displayedFreshness(item))
            assertNotEquals("0", formatSensorValue(readingForDisplay(item), "number"))
        }
    }
    @Test fun `unavailable and nonfinite values never become current regardless of retained history`() {
        for (raw in listOf("NaN", "Infinity", "-Infinity", "1e309", "garbage")) {
            assertNull(readingForDisplay(state(raw)))
            assertEquals(ReadingFreshness.UNAVAILABLE, displayedFreshness(state(raw)))
            assertEquals("Current value unavailable", chartCurrentValue(state(raw)))
        }
        val valid = state("12.34")
        assertNull(readingForDisplay(valid.copy(freshness = ReadingFreshness.UNAVAILABLE)))
        assertNull(readingForDisplay(valid.copy(timeline = valid.timeline!!.copy(latestValid = false))))
        assertEquals("Current value unavailable", chartCurrentValue(valid.copy(freshness = ReadingFreshness.UNAVAILABLE)))
        assertTrue(chartCurrentValue(valid.copy(freshness = ReadingFreshness.STALE)).startsWith("Last known:"))
        assertFalse(chartCurrentValue(valid.copy(freshness = ReadingFreshness.STALE)).contains("Current:"))
    }
}
