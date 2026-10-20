package com.examcamp.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * CSS `box-shadow: 0 <dy> <blur> <color>` (no spread). Compose's shadow() uses an elevation model that is much
 * harder-edged, so this draws the same Gaussian blur the browser does (CSS blur B equals a shadow radius of B).
 * Draw it on a parent with enough padding: the shadow is not clipped by this modifier, but a Popup window is.
 */
fun Modifier.boxShadow(color: Color, dy: Dp, blur: Dp, corner: Dp): Modifier = drawBehind {
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = android.graphics.Color.TRANSPARENT
            setShadowLayer(blur.toPx() / 2f, 0f, dy.toPx(), color.toArgb())
        }
        val r = corner.toPx()
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
    }
}

/** --shadow-sm: 0 1px 2px rgba(8,8,8,.06) light, rgba(0,0,0,.5) dark. */
fun Modifier.shadowSm(dark: Boolean, corner: Dp): Modifier =
    boxShadow(if (dark) Color(0x80000000) else Color(0x0F080808), 1.dp, 2.dp, corner)
