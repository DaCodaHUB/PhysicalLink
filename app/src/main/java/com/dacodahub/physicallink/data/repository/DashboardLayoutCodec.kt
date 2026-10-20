package com.dacodahub.physicallink.data.repository

import com.dacodahub.physicallink.domain.model.*
import org.json.JSONArray
import org.json.JSONObject

/** Version 1: array order is dashboard order. Unknown versions/types reject this device record. */
object DashboardLayoutCodec {
    fun encode(deviceId: String, layout: DashboardLayout): String {
        validate(deviceId, layout)
        return JSONObject().put("version", 1).put("deviceId", deviceId).put("widgets", JSONArray().apply {
            layout.widgets.forEach { widget -> put(JSONObject()
                .put("id", widget.id).put("type", widget.type.name)
                .put("deviceId", widget.source.deviceId).put("capabilityId", widget.source.capabilityId)
                .put("columnSpan", widget.columnSpan).put("rowSpan", widget.rowSpan).put("hidden", widget.hidden)) }
        }).toString()
    }

    fun decode(deviceId: String, text: String): DashboardLayout {
        val root = JSONObject(text)
        require(root.getInt("version") == 1) { "Unsupported dashboard schema" }
        require(root.getString("deviceId") == deviceId) { "Dashboard device mismatch" }
        val array = root.getJSONArray("widgets")
        val layout = DashboardLayout((0 until array.length()).map { index ->
            val widget = array.getJSONObject(index)
            DashboardWidget(widget.getString("id"), WidgetType.valueOf(widget.getString("type")),
                SensorSource(widget.getString("deviceId"), widget.getString("capabilityId")),
                widget.getInt("columnSpan"), widget.getInt("rowSpan"), widget.getBoolean("hidden"))
        })
        validate(deviceId, layout)
        return layout
    }

    private fun validate(deviceId: String, layout: DashboardLayout) {
        require(deviceId.isNotBlank())
        require(layout.widgets.map { it.id }.distinct().size == layout.widgets.size) { "Duplicate widget IDs" }
        require(layout.widgets.all {
            it.id.isNotBlank() && it.source.deviceId == deviceId && it.source.capabilityId.isNotBlank() &&
                it.columnSpan in 1..2 && it.rowSpan > 0
        }) { "Invalid dashboard widget" }
    }
}
