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
    @Test fun `extreme exponents remain bounded text without BigDecimal expansion`() {
        for (value in listOf("1e2147483647", "1e-2147483647", "9e1000000", "1e-1000000")) {
            assertEquals(value, formatSensorValue(value, "number"))
        }
        assertEquals("opaque reading", formatSensorValue("opaque reading", "future-type"))
    }

    @Test fun `compact numbers preserve ordinary readings and bound extreme formatting`() {
        assertEquals("1004.99", compactNumericReading("1004.99"))
        assertEquals("1.23457E+19", compactNumericReading("12345678901234567890"))
        assertEquals("sensor unavailable", compactNumericReading("sensor unavailable"))
        assertEquals("9e2147483647", compactNumericReading("9e2147483647"))
        assertEquals("1E-2147483647", compactNumericReading("1e-2147483647"))
    }
}

