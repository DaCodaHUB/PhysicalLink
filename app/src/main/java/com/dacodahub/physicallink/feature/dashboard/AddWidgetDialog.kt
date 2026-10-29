package com.dacodahub.physicallink.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddWidgetSheet(state: DashboardState, actions: DashboardActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Add or restore widgets", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge) }
            item { Text("Hidden widgets", style = MaterialTheme.typography.titleSmall) }
            if (state.hiddenWidgets.isEmpty()) item { Text("No hidden widgets.") }
            itemsIndexed(state.hiddenWidgets, key = { _, item -> "hidden:${item.widget.id}" }) { index, item ->
                TextButton(onClick = { actions.restore(item.widget.id); onDismiss() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Restore ${(item.capability?.name ?: sensorName(item.widget.source.capabilityId))} (${index + 1})")
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Available sensors", style = MaterialTheme.typography.titleSmall)
                Text("Add another Value widget for any sensor.", style = MaterialTheme.typography.bodySmall)
            }
            if (state.availableSensors.isEmpty()) item { Text("No sensors available right now.") }
            items(state.availableSensors, key = { "source:${it.capabilityId}" }) { source ->
                TextButton(onClick = { actions.add(source); onDismiss() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Add ${(state.sensorNames[source.capabilityId] ?: sensorName(source.capabilityId))}")
                }
            }
            item { OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") } }
        }
    }
}
