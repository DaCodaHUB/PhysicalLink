package com.dacodahub.physicallink.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Shared surface/header/content/status slots for values and numeric visualizations. */
@Composable
internal fun WidgetContainer(
    title: String, modifier: Modifier = Modifier, status: String? = null, controls: WidgetControls? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(modifier.fillMaxWidth().semantics { isTraversalGroup = true }, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(if (controls == null) 16.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(if (controls == null) 12.dp else 6.dp)) {
            WidgetHeader(title, controls)
            if (controls?.target == true) Text("Drop here", Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            content()
            if (status != null) Text(status, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
