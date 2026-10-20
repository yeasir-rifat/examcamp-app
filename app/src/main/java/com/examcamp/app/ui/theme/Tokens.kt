package com.examcamp.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Design tokens taken from styles.css (the final "premium" Webflow layer wins over the base layer). */
@Immutable
class EcColors(
    val isDark: Boolean,
    val primary: Color, val primaryHover: Color, val primaryActive: Color,
    val primaryLight: Color, val primaryBorder: Color, val primaryWash: Color,
    val success: Color, val successLight: Color, val successBorder: Color,
    val danger: Color, val dangerLight: Color, val dangerBorder: Color,
    val warning: Color, val warningLight: Color, val warningBorder: Color,
    val info: Color, val infoLight: Color, val infoBorder: Color,
    val bg: Color, val surface: Color, val surfaceAlt: Color,
    val textPrimary: Color, val textSecondary: Color, val textMuted: Color, val textOnPrimary: Color,
    val border: Color, val borderStrong: Color, val inputBg: Color, val hoverBg: Color, val focusRing: Color,
)

val LightEcColors = EcColors(
    isDark = false,
    primary = Color(0xFF146EF5), primaryHover = Color(0xFF0055D4), primaryActive = Color(0xFF0047B3),
    primaryLight = Color(0xFFEAF2FF), primaryBorder = Color(0xFFB7D2FC), primaryWash = Color(0x0F146EF5),
    success = Color(0xFF00A81C), successLight = Color(0xFFECFDF5), successBorder = Color(0xFFA7F3D0),
    danger = Color(0xFFEE1D36), dangerLight = Color(0xFFFEF2F2), dangerBorder = Color(0xFFFECACA),
    warning = Color(0xFFE08A00), warningLight = Color(0xFFFFFBEB), warningBorder = Color(0xFFFDE68A),
    info = Color(0xFF146EF5), infoLight = Color(0xFFEFF6FF), infoBorder = Color(0xFFBFDBFE),
    bg = Color(0xFFFAF9F6), surface = Color(0xFFFFFFFF), surfaceAlt = Color(0xFFF5F5F5),
    textPrimary = Color(0xFF080808), textSecondary = Color(0xFF5A5A5A), textMuted = Color(0xFF898989),
    textOnPrimary = Color(0xFFFFFFFF),
    border = Color(0xFFD8D8D8), borderStrong = Color(0xFFABABAB), inputBg = Color(0xFFFFFFFF),
    hoverBg = Color(0xFFF5F5F5), focusRing = Color(0x59146EF5),
)

val DarkEcColors = EcColors(
    isDark = true,
    primary = Color(0xFF4A90FF), primaryHover = Color(0xFF70A8FF), primaryActive = Color(0xFF3A7DE6),
    primaryLight = Color(0x244A90FF), primaryBorder = Color(0x664A90FF), primaryWash = Color(0x1A4A90FF),
    success = Color(0xFF00A81C), successLight = Color(0x1F10B981), successBorder = Color(0x4D10B981),
    danger = Color(0xFFEE1D36), dangerLight = Color(0x1FEF4444), dangerBorder = Color(0x4DEF4444),
    warning = Color(0xFFE08A00), warningLight = Color(0x1FF59E0B), warningBorder = Color(0x4DF59E0B),
    info = Color(0xFF4A90FF), infoLight = Color(0x1F3B82F6), infoBorder = Color(0x4D3B82F6),
    bg = Color(0xFF080808), surface = Color(0xFF121212), surfaceAlt = Color(0xFF1A1A1A),
    textPrimary = Color(0xFFF5F5F5), textSecondary = Color(0xFFB8B8B8), textMuted = Color(0xFF8A8A8A),
    textOnPrimary = Color(0xFF080808),
    border = Color(0xFF2C2C2C), borderStrong = Color(0xFF444444), inputBg = Color(0xFF1A1A1A),
    hoverBg = Color(0xFF1F1F1F), focusRing = Color(0x804A90FF),
)

/** Radius scale: --radius-sm / md / lg. */
object Radius {
    val sm = 4.dp
    val md = 6.dp
    val lg = 8.dp
    val pill = 999.dp
}

/** Spacing scale: --space-1 ... --space-16. */
object Space {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s8 = 32.dp
    val s10 = 40.dp
    val s12 = 48.dp
    val s16 = 64.dp
}
