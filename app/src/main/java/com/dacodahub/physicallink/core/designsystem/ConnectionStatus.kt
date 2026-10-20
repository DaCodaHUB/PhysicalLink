package com.dacodahub.physicallink.core.designsystem
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.dacodahub.physicallink.domain.model.ConnectionState

@Composable
fun ConnectionStatus(state: ConnectionState) {
    val message = connectionLabel(state)
    val colors = MaterialTheme.colorScheme
    val (background, foreground) = when (state) {
        ConnectionState.Connected -> colors.primaryContainer to colors.onPrimaryContainer
        ConnectionState.ConnectionLost, is ConnectionState.Error -> colors.errorContainer to colors.onErrorContainer
        else -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    Surface(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = background, contentColor = foreground, shape = MaterialTheme.shapes.medium) {
        Text(message, Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge)
    }
}

internal fun connectionLabel(state: ConnectionState): String = when (state) {
    ConnectionState.Connected -> "Connected"
    ConnectionState.Connecting -> "Connecting"
    ConnectionState.Discovering -> "Loading sensors"
    ConnectionState.Disconnected -> "Not connected"
    ConnectionState.ConnectionLost -> "Connection lost"
    is ConnectionState.Error -> "Could not connect"
}

/** Inline status for compact device headers; text conveys state independently of color. */
@Composable
internal fun DeviceConnectionText(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.Connected -> MaterialTheme.colorScheme.primary
        ConnectionState.ConnectionLost, is ConnectionState.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(connectionLabel(state), style = MaterialTheme.typography.labelSmall, color = color)
    }
}
