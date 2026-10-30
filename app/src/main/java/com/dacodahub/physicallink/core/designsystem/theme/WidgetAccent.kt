package com.dacodahub.physicallink.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

internal val LocalWidgetAccent = staticCompositionLocalOf { Color.Unspecified }

/** Stable presentation color based only on an opaque identity, with no sensor meaning. */
internal fun widgetAccentIndex(identity: String): Int = Math.floorMod(identity.hashCode(), 3)

@Composable
internal fun widgetAccent(identity: String): Color = with(MaterialTheme.colorScheme) {
    when (widgetAccentIndex(identity)) { 0 -> primary; 1 -> secondary; else -> tertiary }
}

@Composable
internal fun currentWidgetAccent(): Color = LocalWidgetAccent.current.let {
    if (it == Color.Unspecified) MaterialTheme.colorScheme.primary else it
}
