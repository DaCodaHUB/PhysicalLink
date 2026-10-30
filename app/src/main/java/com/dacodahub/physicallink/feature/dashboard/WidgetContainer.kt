package com.dacodahub.physicallink.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import com.dacodahub.physicallink.core.designsystem.theme.currentWidgetAccent

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
    statusIsAlert: Boolean = false, content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(modifier.fillMaxWidth().then(if (controls != null) Modifier.border(1.dp,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), MaterialTheme.shapes.large) else Modifier).semantics { isTraversalGroup = true }, shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = if (controls?.target == true) MaterialTheme.colorScheme.primaryContainer
            else if (controls != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f).compositeOver(MaterialTheme.colorScheme.surface)
            else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (controls != null) 3.dp else 1.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(if (controls == null) 12.dp else 6.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.padding(top = 5.dp).size(10.dp).background(currentWidgetAccent(), CircleShape))
                Box(Modifier.weight(1f)) { WidgetHeader(title, controls) }
            }
            if (controls?.target == true) Text("Drop here", Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            content()
            if (status != null) {
                if (statusIsAlert) Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                    Text(status, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                } else Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
