package com.dacodahub.physicallink.core.designsystem
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dacodahub.physicallink.domain.model.ConnectionState

@Composable
fun ConnectionStatus(state: ConnectionState) {
    val message = when (state) {
        ConnectionState.Connected -> "Connected"
        ConnectionState.Connecting -> "Connecting…"
        ConnectionState.Discovering -> "Discovering services…"
        ConnectionState.Disconnected -> "Disconnected"
        ConnectionState.ConnectionLost -> "Connection lost. Select a device to reconnect."
        is ConnectionState.Error -> state.message
    }
    val colors = MaterialTheme.colorScheme
    val (background, foreground) = when (state) {
        ConnectionState.Connected -> colors.primaryContainer to colors.onPrimaryContainer
        ConnectionState.ConnectionLost, is ConnectionState.Error -> colors.errorContainer to colors.onErrorContainer
        else -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    Surface(color = background, contentColor = foreground, shape = MaterialTheme.shapes.medium) {
        Text(message, Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge)
    }
}
