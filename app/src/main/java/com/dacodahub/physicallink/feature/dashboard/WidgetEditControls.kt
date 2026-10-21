package com.dacodahub.physicallink.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dacodahub.physicallink.core.designsystem.ActionGlyph
import com.dacodahub.physicallink.core.designsystem.Glyph

/** Ephemeral card chrome, never part of the saved widget configuration. */
internal data class WidgetControls(
    val index: Int, val dragModifier: Modifier, val enabled: Boolean, val configure: () -> Unit, val target: Boolean = false
)

@Composable
internal fun WidgetHeader(title: String, controls: WidgetControls?) {
    if (controls == null) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(controls.dragModifier.weight(1f).sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics {
                contentDescription = "Drag $title widget ${controls.index + 1}. Hold to move; configuration also provides Move up and Move down."
            }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ActionGlyph(Glyph.DRAG, Modifier.size(20.dp))
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = controls.configure, enabled = controls.enabled,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "Configure $title widget ${controls.index + 1}" }) {
                ActionGlyph(Glyph.SETTINGS)
            }
        }
    }
}
