package com.dacodahub.physicallink.feature.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class SensorFormattingTest {
    @Test
    fun `numeric readings use two places and omit whole number decimals`() {
        assertEquals("23.46", formatSensorValue("23.456", "number"))
        assertEquals("23.40", formatSensorValue("23.4", "number"))
        assertEquals("42", formatSensorValue("42.000", "number"))
        assertEquals("-1.24", formatSensorValue("-1.235", "number"))
        assertEquals("0", formatSensorValue("-0.001", "number"))
    }

    @Test
    fun `missing readings and non numeric capabilities are preserved`() {
        assertEquals("--", formatSensorValue(null, "number"))
        assertEquals("--", formatSensorValue(" ", "number"))
        assertEquals("0012", formatSensorValue("0012", "text"))
        assertEquals("ready", formatSensorValue("ready", "number"))
    }

    @Test
    fun `units and dynamic identifiers are readable`() {
        assertEquals("", sensorUnit("RAW"))
        assertEquals("\u00B0C", sensorUnit("C"))
        assertEquals("\u00B0C", sensorUnit("celsius"))
        assertEquals("%", sensorUnit("%"))
        assertEquals("Soil moisture", sensorName("soil_moisture"))
        assertEquals("Air Temperature", sensorName("airTemperature"))
        assertEquals("Light level", sensorName("light-level"))
    }
}

