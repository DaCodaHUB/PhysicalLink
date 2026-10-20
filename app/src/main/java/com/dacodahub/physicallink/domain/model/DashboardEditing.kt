package com.dacodahub.physicallink.domain.model

/** Move to the target's position, preserving every widget's identity and source. */
fun DashboardLayout.moveWidget(id: String, targetId: String): DashboardLayout {
    val from = widgets.indexOfFirst { it.id == id && !it.hidden }
    val to = widgets.indexOfFirst { it.id == targetId && !it.hidden }
    if (from < 0 || to < 0 || from == to) return this
    val reordered = widgets.toMutableList()
    reordered.add(to, reordered.removeAt(from))
    return copy(widgets = reordered.toList())
}

fun DashboardLayout.moveWidgetBy(id: String, direction: Int): DashboardLayout {
    val visible = widgets.filterNot { it.hidden }
    val index = visible.indexOfFirst { it.id == id }
    if (index < 0 || direction !in listOf(-1, 1)) return this
    val target = visible.getOrNull(index + direction) ?: return this
    return moveWidget(id, target.id)
}

fun DashboardLayout.toggleWidgetWidth(id: String): DashboardLayout = copy(
    widgets = widgets.map { if (it.id == id) it.copy(columnSpan = if (it.columnSpan == 2) 1 else 2) else it }
)

fun DashboardLayout.setWidgetHidden(id: String, hidden: Boolean): DashboardLayout = copy(
    widgets = widgets.map { if (it.id == id) it.copy(hidden = hidden) else it }
)

/** Caller supplies a fresh instance ID; duplicate sources are intentionally allowed. */
fun DashboardLayout.addValueWidget(id: String, source: SensorSource): DashboardLayout {
    if (id.isBlank() || widgets.any { it.id == id } || source.deviceId.isBlank() || source.capabilityId.isBlank()) return this
    return copy(widgets = widgets + DashboardWidget(id, WidgetType.VALUE, source))
}

/** Explicit selection is idempotent even when several taps queue before the first disk commit. */
fun DashboardLayout.resizeWidget(id: String, columns: Int): DashboardLayout {
    if (columns !in 1..2) return this
    return copy(widgets = widgets.map { if (it.id == id) it.copy(columnSpan = columns) else it })
}

fun DashboardLayout.setWidgetType(id: String, type: WidgetType): DashboardLayout = copy(
    widgets = widgets.map { if (it.id == id) it.copy(type = type) else it }
)
