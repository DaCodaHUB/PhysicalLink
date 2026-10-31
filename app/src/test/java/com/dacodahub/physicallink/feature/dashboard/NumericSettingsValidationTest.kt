package com.dacodahub.physicallink.feature.dashboard

import com.dacodahub.physicallink.domain.model.*
import org.junit.*
import org.junit.Assert.*

class NumericSettingsValidationTest {
    private val base = WidgetSettings(minimum = -10.0, maximum = 10.0)
    @Test fun `invalid text and intermediate input remain invalid instead of becoming zero`() {
        for (raw in listOf("", " ", "-", ".", "-.", "1e", "nonsense", "1 2", "NaN", "Infinity", "-Infinity", "1e309", "1e-999", "0x1.0p2")) {
            val result = validateNumericSettings(WidgetType.GAUGE, base, NumericSettingsDraft.from(base).copy(minimum = raw))
            assertNull("Must reject '$raw'", result.settings)
            assertNotNull(result.errors[NumericField.MINIMUM])
            assertEquals(-10.0, base.minimum!!, 0.0)
        }
    }
    @Test fun `signed decimal scientific whitespace and finite extremes are supported`() {
        val draft = NumericSettingsDraft.from(base).copy(minimum = " -1.7976931348623157e308 ", maximum = "1.7976931348623157e308", precision = " 6 ", freshness = " 30 ")
        val valid = validateNumericSettings(WidgetType.RANGE, base, draft).settings!!
        assertTrue(valid.hasRange)
        assertEquals(0.5f, rangeReading(0.0, valid)!!.fraction, 0f)
        val tiny = validateNumericSettings(WidgetType.GAUGE, base, draft.copy(minimum = "-1e-300", maximum = "+.2e-298"))
        assertNotNull(tiny.settings)
        assertTrue(tiny.settings!!.minimum!! < 0)
    }
    @Test fun `integer fields reject decimals overflow signs and out of range values`() {
        for (raw in listOf("", "3.0", "1e0", "-1", "7", "2147483648", "NaN", "abc")) {
            assertNotNull(validateNumericSettings(WidgetType.VALUE, base, NumericSettingsDraft.from(base).copy(precision = raw)).errors[NumericField.PRECISION])
        }
        for (raw in listOf("0", "-1", "1.5", "3601", "Infinity")) {
            assertNotNull(validateNumericSettings(WidgetType.VALUE, base, NumericSettingsDraft.from(base).copy(freshness = raw)).errors[NumericField.FRESHNESS])
        }
    }
    @Test fun `optional thresholds stay null and invalid bounds or thresholds disable saving`() {
        assertNull(validateNumericSettings(WidgetType.GAUGE, base, NumericSettingsDraft.from(base)).settings!!.lowerThreshold)
        for (draft in listOf(NumericSettingsDraft.from(base).copy(maximum = "-10"), NumericSettingsDraft.from(base).copy(minimum = "20"),
            NumericSettingsDraft.from(base).copy(lower = "12"), NumericSettingsDraft.from(base).copy(lower = "5", upper = "-5"),
            NumericSettingsDraft.from(base).copy(upper = "pasted text"))) {
            assertNull(validateNumericSettings(WidgetType.RANGE, base, draft).settings)
        }
    }
    @Test fun `chart axes support all four independent modes and reset while retaining other settings`() {
        for (autoMin in listOf(false, true)) for (autoMax in listOf(false, true)) {
            val draft = NumericSettingsDraft.from(base).copy(autoMinimum = autoMin, autoMaximum = autoMax, chartMinimum = "-5.25", chartMaximum = "6e2")
            val settings = validateNumericSettings(WidgetType.CHART, base, draft).settings!!
            assertEquals(if (autoMin) null else -5.25, settings.chartMinimum)
            assertEquals(if (autoMax) null else 600.0, settings.chartMaximum)
            assertEquals(base.minimum, settings.minimum)
        }
        val auto = NumericSettingsDraft.from(base).copy(autoMinimum = true, autoMaximum = true, chartMinimum = "invalid", chartMaximum = "invalid")
        assertNull(validateNumericSettings(WidgetType.CHART, base, auto).settings!!.chartMinimum)
        assertNull(validateNumericSettings(WidgetType.CHART, base, auto.copy(autoMinimum = false)).settings)
        assertNull(validateNumericSettings(WidgetType.CHART, base, auto.copy(autoMinimum = false, chartMinimum = Double.MAX_VALUE.toString())).settings)
        assertNull(validateNumericSettings(WidgetType.CHART, base, auto.copy(autoMaximum = false, chartMaximum = (-Double.MAX_VALUE).toString())).settings)
        assertNull(validateNumericSettings(WidgetType.CHART, base, auto.copy(autoMinimum = false, autoMaximum = false, chartMinimum = "5", chartMaximum = "5")).settings)
    }
}
