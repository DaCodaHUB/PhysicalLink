package com.dacodahub.physicallink.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

internal enum class Glyph { BACK, EDIT, MORE, SETTINGS, DRAG, ADD, INFO }

/** Small native Compose glyphs; action labels belong to their 48dp buttons. */
@Composable
internal fun ActionGlyph(glyph: Glyph, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        val unit = size.minDimension / 24f
        fun line(x: Float, y: Float, endX: Float, endY: Float) = drawLine(color,
            Offset(x * unit, y * unit), Offset(endX * unit, endY * unit), 2f * unit, StrokeCap.Round)
        when (glyph) {
            Glyph.BACK -> { line(20f, 12f, 4f, 12f); line(4f, 12f, 11f, 5f); line(4f, 12f, 11f, 19f) }
            Glyph.EDIT -> {
                val pencil = Path().apply {
                    moveTo(4f * unit, 20f * unit); lineTo(5f * unit, 14f * unit)
                    lineTo(16f * unit, 3f * unit); lineTo(21f * unit, 8f * unit)
                    lineTo(10f * unit, 19f * unit); close()
                }
                drawPath(pencil, color, style = Stroke(2f * unit)); line(14f, 5f, 19f, 10f)
            }
            Glyph.MORE -> listOf(5f, 12f, 19f).forEach { drawCircle(color, 1.8f * unit, Offset(12f * unit, it * unit)) }
            Glyph.SETTINGS -> {
                line(4f, 6f, 20f, 6f); line(4f, 12f, 20f, 12f); line(4f, 18f, 20f, 18f)
                listOf(Offset(8f, 6f), Offset(16f, 12f), Offset(10f, 18f)).forEach {
                    drawCircle(color, 3f * unit, it * unit)
                }
            }
            Glyph.DRAG -> listOf(8f, 16f).forEach { x -> listOf(6f, 12f, 18f).forEach { y ->
                drawCircle(color, 1.4f * unit, Offset(x * unit, y * unit))
            } }
            Glyph.ADD -> { line(5f, 12f, 19f, 12f); line(12f, 5f, 12f, 19f) }
            Glyph.INFO -> {
                drawCircle(color, 9f * unit, center, style = Stroke(2f * unit))
                line(12f, 11f, 12f, 17f); drawCircle(color, 1f * unit, Offset(12f * unit, 7f * unit))
            }
        }
    }
}
