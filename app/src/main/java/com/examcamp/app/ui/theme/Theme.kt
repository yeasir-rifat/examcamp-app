package com.examcamp.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.examcamp.app.i18n.Lang
import com.examcamp.app.i18n.LocalLang

enum class ThemeMode(val code: String) {
    LIGHT("light"), DARK("dark"), SYSTEM("system");

    companion object {
        fun from(code: String?): ThemeMode = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

@Immutable
class EcType(
    val h1: TextStyle, val h2: TextStyle, val h3: TextStyle, val h4: TextStyle,
    val body: TextStyle, val bodyStrong: TextStyle,
    val small: TextStyle, val smallStrong: TextStyle,
    val caption: TextStyle, val button: TextStyle, val label: TextStyle,
)

/**
 * Values measured from the website's computed styles (Chromium, English).
 * Phones (width <= 640): h1 1.625rem, h2 1.3125rem, h3 1.125rem (the mobile block in styles.css).
 * Wider screens: h1 clamp(2.25rem, 4.5vw, 3.5rem) -> 36, h2 clamp(1.75rem, 3vw, 2.25rem) -> 28, h3 1.5rem.
 * h4 1.125rem, body 1rem, small .875rem, label .8125rem, caption .75rem on both.
 * Body line-height 1.6 is inherited by small/label; caption is 1.5; h4 is 1.3; h1-h3 are 1.1 with -0.03em.
 */
fun buildType(f: EcFonts, compact: Boolean = true, bengali: Boolean = false): EcType {
    // [lang="bn"] sets letter-spacing: normal !important in styles.css, so Bengali never gets tracking.
    fun s(size: Float, bold: Boolean, lh: Float, ls: Float = 0f) = TextStyle(
        fontFamily = if (bold) f.bold else f.regular,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontSize = size.sp,
        lineHeight = (size * lh).sp,
        letterSpacing = if (bengali) 0.em else ls.em,
    )
    // English h1-h3 are 1.1 (a rule on html[lang=en]); Bengali headings keep the base 1.3, h1 excepted (1.1).
    val h23 = if (bengali) 1.3f else 1.1f
    return EcType(
        h1 = s(if (compact) 26f else 36f, true, 1.1f, -0.03f),
        h2 = s(if (compact) 21f else 28f, true, h23, -0.03f),
        h3 = s(if (compact) 18f else 24f, true, h23, -0.03f),
        h4 = s(18f, true, 1.3f, -0.01f),
        body = s(16f, false, 1.6f, -0.005f),
        bodyStrong = s(16f, true, 1.6f, -0.005f),
        small = s(14f, false, 1.6f, -0.005f),
        smallStrong = s(14f, true, 1.6f, -0.005f),
        caption = s(12f, false, 1.5f, -0.005f),
        button = s(16f, true, 1.2f, -0.01f),
        label = s(13f, true, 1.6f, -0.005f),
    )
}

val LocalEcColors = compositionLocalOf { LightEcColors }
val LocalEcType = compositionLocalOf<EcType> { buildType(EcFonts(FontFamily.SansSerif, FontFamily.SansSerif)) }

/** Short accessor used across components: Ec.c.primary, Ec.t.body. */
object Ec {
    val c: EcColors @Composable @ReadOnlyComposable get() = LocalEcColors.current
    val t: EcType @Composable @ReadOnlyComposable get() = LocalEcType.current
}

@Composable
fun ExamcampTheme(mode: ThemeMode, lang: Lang, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val fonts = remember { FontLoader.load(context) }
    val compact = LocalConfiguration.current.screenWidthDp <= 640
    val type = remember(fonts, compact, lang) { buildType(fonts, compact, lang == Lang.BN) }
    val colors = if (dark) DarkEcColors else LightEcColors
    val scheme = if (dark) {
        darkColorScheme(primary = colors.primary, onPrimary = colors.textOnPrimary, background = colors.bg,
            onBackground = colors.textPrimary, surface = colors.surface, onSurface = colors.textPrimary)
    } else {
        lightColorScheme(primary = colors.primary, onPrimary = colors.textOnPrimary, background = colors.bg,
            onBackground = colors.textPrimary, surface = colors.surface, onSurface = colors.textPrimary)
    }
    CompositionLocalProvider(
        LocalEcColors provides colors,
        LocalEcType provides type,
        LocalLang provides lang,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
