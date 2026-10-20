package com.examcamp.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Icons converted 1:1 from the website's inline SVGs (index.html). Stroke icons are drawn black and tinted at use. */
object EcIcons {
    private class P(val d: String, val sw: Float, val fill: Long?)

    private fun build(name: String, minX: Float, minY: Float, w: Float, h: Float, ps: List<P>): ImageVector {
        val b = ImageVector.Builder(name, w.dp, h.dp, w, h)
        b.addGroup(translationX = -minX, translationY = -minY)
        for (p in ps) {
            val nodes = PathParser().parsePathString(p.d).toNodes()
            if (p.fill != null) b.addPath(nodes, fill = SolidColor(Color(p.fill)))
            else b.addPath(nodes, stroke = SolidColor(Color.Black), strokeLineWidth = p.sw, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
        b.clearGroup()
        return b.build()
    }

    val Sun: ImageVector by lazy {
        build("Sun", 0f, 0f, 24f, 24f, listOf(
            P("M7.8 12a4.2 4.2 0 1 0 8.4 0a4.2 4.2 0 1 0 -8.4 0Z", 1.8f, null),
            P("M12 2.5v2.2M12 19.3v2.2M4.2 4.2l1.6 1.6M18.2 18.2l1.6 1.6M2.5 12h2.2M19.3 12h2.2M4.2 19.8l1.6-1.6M18.2 5.8l1.6-1.6", 1.8f, null)
        ))
    }
    val Moon: ImageVector by lazy {
        build("Moon", 0f, 0f, 24f, 24f, listOf(
            P("M20.5 14.5a8.5 8.5 0 1 1-9-11 6.8 6.8 0 0 0 9 11Z", 1.8f, null)
        ))
    }
    val Gear: ImageVector by lazy {
        build("Gear", 0f, 0f, 24f, 24f, listOf(
            P("M10.325 4.317c.426 -1.756 2.924 -1.756 3.35 0a1.724 1.724 0 0 0 2.573 1.066c1.543 -.94 3.31 .826 2.37 2.37a1.724 1.724 0 0 0 1.065 2.572c1.756 .426 1.756 2.924 0 3.35a1.724 1.724 0 0 0 -1.066 2.573c.94 1.543 -.826 3.31 -2.37 2.37a1.724 1.724 0 0 0 -2.572 1.065c-.426 1.756 -2.924 1.756 -3.35 0a1.724 1.724 0 0 0 -2.573 -1.066c-1.543 .94 -3.31 -.826 -2.37 -2.37a1.724 1.724 0 0 0 -1.065 -2.572c-1.756 -.426 -1.756 -2.924 0 -3.35a1.724 1.724 0 0 0 1.066 -2.573c-.94 -1.543 .826 -3.31 2.37 -2.37c1 .608 2.296 .07 2.572 -1.065z", 1.7f, null),
            P("M9 12a3 3 0 1 0 6 0a3 3 0 0 0 -6 0", 1.7f, null)
        ))
    }
    val Google: ImageVector by lazy {
        build("Google", 0f, 0f, 18f, 18f, listOf(
            P("M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.9c1.7-1.57 2.7-3.88 2.7-6.62z", 1.7f, 0xFF4285F4L),
            P("M9 18c2.43 0 4.47-.8 5.96-2.18l-2.9-2.26c-.8.54-1.84.86-3.06.86-2.35 0-4.34-1.59-5.05-3.72H.98v2.33A9 9 0 0 0 9 18z", 1.7f, 0xFF34A853L),
            P("M3.95 10.7A5.4 5.4 0 0 1 3.66 9c0-.59.1-1.17.28-1.7V4.96H.98A9 9 0 0 0 0 9c0 1.45.35 2.83.98 4.04l2.97-2.33z", 1.7f, 0xFFFBBC05L),
            P("M9 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.58C13.46.89 11.43 0 9 0A9 9 0 0 0 .98 4.96l2.97 2.34C4.66 5.17 6.65 3.58 9 3.58z", 1.7f, 0xFFEA4335L)
        ))
    }
    val Grid: ImageVector by lazy {
        build("Grid", 0f, 0f, 24f, 24f, listOf(
            P("M4.6 3h3.8a1.6 1.6 0 0 1 1.6 1.6v3.8a1.6 1.6 0 0 1 -1.6 1.6h-3.8a1.6 1.6 0 0 1 -1.6 -1.6v-3.8a1.6 1.6 0 0 1 1.6 -1.6Z", 1.8f, null),
            P("M15.6 3h3.8a1.6 1.6 0 0 1 1.6 1.6v3.8a1.6 1.6 0 0 1 -1.6 1.6h-3.8a1.6 1.6 0 0 1 -1.6 -1.6v-3.8a1.6 1.6 0 0 1 1.6 -1.6Z", 1.8f, null),
            P("M4.6 14h3.8a1.6 1.6 0 0 1 1.6 1.6v3.8a1.6 1.6 0 0 1 -1.6 1.6h-3.8a1.6 1.6 0 0 1 -1.6 -1.6v-3.8a1.6 1.6 0 0 1 1.6 -1.6Z", 1.8f, null),
            P("M15.6 14h3.8a1.6 1.6 0 0 1 1.6 1.6v3.8a1.6 1.6 0 0 1 -1.6 1.6h-3.8a1.6 1.6 0 0 1 -1.6 -1.6v-3.8a1.6 1.6 0 0 1 1.6 -1.6Z", 1.8f, null)
        ))
    }
    val Sparkle: ImageVector by lazy {
        build("Sparkle", 0f, 0f, 24f, 24f, listOf(
            P("M7.5 3h9a4.5 4.5 0 0 1 4.5 4.5v9a4.5 4.5 0 0 1 -4.5 4.5h-9a4.5 4.5 0 0 1 -4.5 -4.5v-9a4.5 4.5 0 0 1 4.5 -4.5Z", 1.8f, null),
            P("M12 7.2l1.35 3.45 3.45 1.35-3.45 1.35L12 16.8l-1.35-3.45L7.2 12l3.45-1.35L12 7.2Z", 1.6f, null)
        ))
    }
    val Live: ImageVector by lazy {
        build("Live", -2f, -2f, 28f, 28f, listOf(
            P("M8.8 12a3.2 3.2 0 1 0 6.4 0a3.2 3.2 0 1 0 -6.4 0Z", 1.7f, null),
            P("M6.2 6.2a8.1 8.1 0 0 0 0 11.6M17.8 6.2a8.1 8.1 0 0 1 0 11.6", 1.7f, null),
            P("M3.2 3.2a12.6 12.6 0 0 0 0 17.6M20.8 3.2a12.6 12.6 0 0 1 0 17.6", 1.7f, null)
        ))
    }
    val Practice: ImageVector by lazy {
        build("Practice", 0f, 0f, 24f, 24f, listOf(
            P("M4 19.5A2.5 2.5 0 0 1 6.5 17H20", 1.7f, null),
            P("M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z", 1.7f, null),
            P("M9 8h7M9 11.5h7", 1.7f, null)
        ))
    }
    val Stats: ImageVector by lazy {
        build("Stats", 0f, 0f, 24f, 24f, listOf(
            P("M5 19V10M12 19V5M19 19v-7", 1.9f, null)
        ))
    }
    val Chevron: ImageVector by lazy {
        build("Chevron", 0f, 0f, 24f, 24f, listOf(
            P("M9 6l6 6-6 6", 2.2f, null)
        ))
    }
    val History: ImageVector by lazy {
        build("History", 0f, 0f, 24f, 24f, listOf(
            P("M3 12a9 9 0 1 0 3-6.7", 1.7f, null),
            P("M3 4v5h5", 1.7f, null)
        ))
    }
    val ChevronLeft: ImageVector by lazy {
        build("ChevronLeft", 0f, 0f, 24f, 24f, listOf(P("M15 6l-6 6l6 6", 1.85f, null)))
    }
    val ChevronDown: ImageVector by lazy {
        build("ChevronDown", 0f, 0f, 24f, 24f, listOf(P("M6 9l6 6 6-6", 2f, null)))
    }
    val Eye: ImageVector by lazy {
        build("Eye", 0f, 0f, 24f, 24f, listOf(
            P("M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z", 1.7f, null),
            P("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z", 1.7f, null)
        ))
    }
    val EyeOff: ImageVector by lazy {
        build("EyeOff", 0f, 0f, 24f, 24f, listOf(
            P("M3 3l18 18M10.6 10.6a3 3 0 0 0 4.24 4.24M9.9 5.1A10.4 10.4 0 0 1 12 5c6.5 0 10 7 10 7a13.4 13.4 0 0 1-3.2 4.1M6.5 6.6C4 8.3 2 12 2 12s3.5 7 10 7a10.4 10.4 0 0 0 3-.44", 1.7f, null)
        ))
    }
    val Camera: ImageVector by lazy {
        build("Camera", 0f, 0f, 24f, 24f, listOf(
            P("M4 8.5A1.5 1.5 0 0 1 5.5 7h2l1-2h7l1 2h2A1.5 1.5 0 0 1 20 8.5v9A1.5 1.5 0 0 1 18.5 19h-13A1.5 1.5 0 0 1 4 17.5v-9Z", 1.6f, null),
            P("M8.8 13a3.2 3.2 0 1 0 6.4 0a3.2 3.2 0 1 0 -6.4 0Z", 1.6f, null)
        ))
    }
    val Upload: ImageVector by lazy {
        build("Upload", 0f, 0f, 24f, 24f, listOf(
            P("M12 15V4M8 8l4-4 4 4", 1.6f, null),
            P("M4 15v3.5A1.5 1.5 0 0 0 5.5 20h13a1.5 1.5 0 0 0 1.5-1.5V15", 1.6f, null)
        ))
    }
    val Close: ImageVector by lazy {
        build("Close", 0f, 0f, 24f, 24f, listOf(P("M6 6l12 12M18 6L6 18", 1.8f, null)))
    }
    val Plus: ImageVector by lazy {
        build("Plus", 0f, 0f, 24f, 24f, listOf(P("M12 5v14M5 12h14", 2f, null)))
    }
    val File: ImageVector by lazy {
        build("File", 0f, 0f, 24f, 24f, listOf(
            P("M7 3h7l5 5v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z", 1.6f, null),
            P("M14 3v5h5", 1.6f, null)
        ))
    }
    val Check: ImageVector by lazy {
        build("Check", 0f, 0f, 24f, 24f, listOf(P("M5 12.5l4.5 4.5L19 7", 2.2f, null)))
    }
    val Bookmark: ImageVector by lazy {
        build("Bookmark", 0f, 0f, 24f, 24f, listOf(P("M6 3.75A1.75 1.75 0 0 1 7.75 2h8.5A1.75 1.75 0 0 1 18 3.75V21l-6-4-6 4V3.75Z", 1.8f, null)))
    }
    val Home: ImageVector by lazy {
        build("Home", 0f, 0f, 24f, 24f, listOf(
            P("M5 12l-2 0l9 -9l9 9l-2 0", 1.85f, null),
            P("M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2v-7", 1.85f, null),
            P("M9 21v-6a2 2 0 0 1 2 -2h2a2 2 0 0 1 2 2v6", 1.85f, null)
        ))
    }
    val HelpCircle: ImageVector by lazy {
        build("HelpCircle", 0f, 0f, 24f, 24f, listOf(
            P("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0Z", 2f, null),
            P("M9.5 9a2.5 2.5 0 0 1 4.7-1.2c.5.9.1 1.5-.6 2.1-.7.6-1.3 1-1.4 2.1", 2f, null)
        ))
    }
}
