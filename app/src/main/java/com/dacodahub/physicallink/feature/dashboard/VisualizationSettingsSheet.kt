package com.dacodahub.physicallink.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dacodahub.physicallink.domain.model.DashboardWidget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VisualizationSettingsSheet(widget: DashboardWidget, onSave: (com.dacodahub.physicallink.domain.model.WidgetSettings) -> Unit, onDismiss: () -> Unit) {
    var draft by remember(widget.source, widget.id, widget.settings) { mutableStateOf(NumericSettingsDraft.from(widget.settings)) }
    val validation = remember(widget.type, widget.settings, draft) { validateNumericSettings(widget.type, widget.settings, draft) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Visualization settings", style = MaterialTheme.typography.titleLarge)
            NumericSettingsFields(widget.type, true, draft, validation) { draft = it }
            Button(onClick = { validation.settings?.let(onSave) }, enabled = validation.settings != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Save settings") }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}
