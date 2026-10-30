package com.dacodahub.physicallink.core.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dacodahub.physicallink.R

internal enum class Glyph { BACK, EDIT, MORE, SETTINGS, DRAG, ADD, INFO }

/** Monochrome Material vectors; accessible action labels belong to the parent buttons. */
@Composable
internal fun ActionGlyph(glyph: Glyph, modifier: Modifier = Modifier) {
    val resource = when (glyph) {
        Glyph.BACK -> R.drawable.ic_back
        Glyph.EDIT -> R.drawable.ic_edit
        Glyph.MORE -> R.drawable.ic_more
        Glyph.SETTINGS -> R.drawable.ic_settings
        Glyph.DRAG -> R.drawable.ic_drag
        Glyph.ADD -> R.drawable.ic_add
        Glyph.INFO -> R.drawable.ic_info
    }
    Icon(painterResource(resource), contentDescription = null, modifier = modifier.size(24.dp), tint = LocalContentColor.current)
}
