package com.dacodahub.physicallink.data.repository

import com.dacodahub.physicallink.domain.model.*
import org.json.JSONArray
import org.json.JSONObject

/** Version 2 adds per-widget settings and RANGE; v1 reads migrate without changing widget identity. */
object DashboardLayoutCodec {
    fun encode(deviceId: String, layout: DashboardLayout): String {
        validate(deviceId, layout)
        return JSONObject().put("version", 2).put("deviceId", deviceId).put("widgets", JSONArray().apply {
            layout.widgets.forEach { widget ->
                val settings = widget.settings
                put(JSONObject().put("id", widget.id).put("type", widget.type.name)
                    .put("deviceId", widget.source.deviceId).put("capabilityId", widget.source.capabilityId)
                    .put("columnSpan", widget.columnSpan).put("rowSpan", widget.rowSpan).put("hidden", widget.hidden)
                    .put("settings", JSONObject().put("chartWindowSeconds", settings.chartWindowSeconds)
                        .put("minimum", settings.minimum ?: JSONObject.NULL).put("maximum", settings.maximum ?: JSONObject.NULL)
                        .put("lowerThreshold", settings.lowerThreshold ?: JSONObject.NULL).put("upperThreshold", settings.upperThreshold ?: JSONObject.NULL)
                        .put("precision", settings.precision).put("staleAfterSeconds", settings.staleAfterSeconds)))
            }
        }).toString()
    }
    fun isLegacy(text: String): Boolean = JSONObject(text).integer("version", -1) == 1

    fun decode(deviceId: String, text: String): DashboardLayout {
        val root = JSONObject(text)
        val version = root.integer("version", -1)
        require(version in 1..2) { "Unsupported dashboard schema" }
        require(root.getString("deviceId") == deviceId) { "Dashboard device mismatch" }
        val array = root.getJSONArray("widgets")
        val layout = DashboardLayout((0 until array.length()).map { index ->
            val widget = array.getJSONObject(index)
            val settings = if (version == 1 || !widget.has("settings")) WidgetSettings() else {
                val saved = widget.getJSONObject("settings")
                WidgetSettings(saved.integer("chartWindowSeconds", 60), saved.numberOrNull("minimum"), saved.numberOrNull("maximum"),
                    saved.numberOrNull("lowerThreshold"), saved.numberOrNull("upperThreshold"),
                    saved.integer("precision", 2), saved.integer("staleAfterSeconds", 15))
            }
            DashboardWidget(widget.getString("id"), WidgetType.valueOf(widget.getString("type")),
                SensorSource(widget.getString("deviceId"), widget.getString("capabilityId")),
                widget.getInt("columnSpan"), widget.getInt("rowSpan"), widget.getBoolean("hidden"), settings)
        })
        validate(deviceId, layout)
        return layout
    }
    private fun JSONObject.integer(key: String, default: Int): Int {
        if (!has(key)) return default
        val raw = get(key)
        require(raw is Number && raw.toDouble().isFinite() && raw.toDouble() == raw.toInt().toDouble()) { "Invalid integer setting" }
        return raw.toInt()
    }
    private fun JSONObject.numberOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val number = get(key)
        require(number is Number) { "Invalid numeric setting" }
        return number.toDouble()
    }
    private fun validate(deviceId: String, layout: DashboardLayout) {
        require(deviceId.isNotBlank())
        require(layout.widgets.map { it.id }.distinct().size == layout.widgets.size) { "Duplicate widget IDs" }
        require(layout.widgets.all {
            it.id.isNotBlank() && it.source.deviceId == deviceId && it.source.capabilityId.isNotBlank() &&
                it.columnSpan in 1..2 && it.rowSpan > 0 && it.settings.validationError() == null
        }) { "Invalid dashboard widget" }
    }
}
