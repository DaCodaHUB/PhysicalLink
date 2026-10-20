package com.dacodahub.physicallink.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Presentation kinds only. RANGE is deliberately not added to the persisted WidgetType enum. */
internal enum class WidgetPresentation(val label: String) { VALUE("Value"), GAUGE("Gauge"), CHART("Chart"), RANGE("Range") }

/** Shared surface/header/content/status slots for current values and future visualizations. */
@Composable
internal fun WidgetContainer(
    title: String, modifier: Modifier = Modifier, status: String? = null, controls: WidgetControls? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WidgetHeader(title, controls)
            if (controls?.target == true) Text("Drop here", Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            content()
            if (status != null) Text(status, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Explicitly nonfunctional, with no mock scale, graph, progress, or selectable range. */
@Composable
internal fun UnavailableVisualization(title: String, presentation: WidgetPresentation, controls: WidgetControls? = null) {
    WidgetContainer(title, controls = controls) {
        Text(presentation.label, style = MaterialTheme.typography.titleLarge)
        Text("${presentation.label} visualization is not available yet.", style = MaterialTheme.typography.bodyMedium)
    }
}
