package com.dacodahub.physicallink.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dacodahub.physicallink.domain.model.WidgetType

@Composable
internal fun NumericSettingsFields(type: WidgetType, numeric: Boolean, draft: NumericSettingsDraft,
    validation: NumericSettingsValidation, inlineDetails: Boolean = false, onChange: (NumericSettingsDraft) -> Unit) {
    if (type == WidgetType.CHART) {
        if (!inlineDetails) {
            Text("Chart time window")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60, 300, 900).forEach { seconds -> FilterChip(selected = draft.window == seconds,
                    onClick = { onChange(draft.copy(window = seconds)) }, label = { Text("${seconds / 60} min") }, modifier = Modifier.heightIn(min = 48.dp)) }
            }
        }
        Text("Y-axis minimum", style = MaterialTheme.typography.titleSmall)
        AxisMode(draft.autoMinimum, { onChange(draft.copy(autoMinimum = it)) }, "minimum")
        if (!draft.autoMinimum) NumericInput("Chart minimum", draft.chartMinimum, validation.errors[NumericField.CHART_MINIMUM]) { onChange(draft.copy(chartMinimum = it)) }
        Text("Y-axis maximum", style = MaterialTheme.typography.titleSmall)
        AxisMode(draft.autoMaximum, { onChange(draft.copy(autoMaximum = it)) }, "maximum")
        if (!draft.autoMaximum) NumericInput("Chart maximum", draft.chartMaximum, validation.errors[NumericField.CHART_MAXIMUM]) { onChange(draft.copy(chartMaximum = it)) }
        TextButton(onClick = { onChange(draft.copy(autoMinimum = true, autoMaximum = true, chartMinimum = "", chartMaximum = "")) }) { Text("Reset axes to Auto") }
    }
    if (type == WidgetType.GAUGE || type == WidgetType.RANGE) {
        NumericInput("Minimum", draft.minimum, validation.errors[NumericField.MINIMUM]) { onChange(draft.copy(minimum = it)) }
        NumericInput("Maximum", draft.maximum, validation.errors[NumericField.MAXIMUM]) { onChange(draft.copy(maximum = it)) }
        if (!inlineDetails || draft.lower.isNotEmpty()) NumericInput("Lower threshold (optional)", draft.lower, validation.errors[NumericField.LOWER]) { onChange(draft.copy(lower = it)) }
        if (!inlineDetails || draft.upper.isNotEmpty()) NumericInput("Upper threshold (optional)", draft.upper, validation.errors[NumericField.UPPER]) { onChange(draft.copy(upper = it)) }
    }
    if (numeric) NumericInput("Decimal places (0-6)", draft.precision, validation.errors[NumericField.PRECISION], integer = true) { onChange(draft.copy(precision = it)) }
    if (!inlineDetails) NumericInput("Stale after (seconds)", draft.freshness, validation.errors[NumericField.FRESHNESS], integer = true) { onChange(draft.copy(freshness = it)) }
    validation.errors[NumericField.GENERAL]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}
@Composable
private fun AxisMode(auto: Boolean, onChange: (Boolean) -> Unit, name: String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = auto, onClick = { onChange(true) }, label = { Text("Auto $name") }, modifier = Modifier.heightIn(min = 48.dp))
        FilterChip(selected = !auto, onClick = { onChange(false) }, label = { Text("Custom $name") }, modifier = Modifier.heightIn(min = 48.dp))
    }
}
@Composable
private fun NumericInput(label: String, value: String, error: String?, integer: Boolean = false, onChange: (String) -> Unit) {
    SignedDecimalKeyboard {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        isError = error != null, supportingText = if (error != null) ({ Text(error) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal, imeAction = ImeAction.Next))
    }
}
