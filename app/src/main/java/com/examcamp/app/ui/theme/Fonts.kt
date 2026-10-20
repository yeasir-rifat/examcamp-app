package com.examcamp.app.ui.theme

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

class EcFonts(val regular: FontFamily, val bold: FontFamily)

/**
 * The website uses Lato for Latin text and Noto Serif Bengali for Bengali glyphs.
 * The font files are expected in assets/fonts (the GitHub workflow downloads them);
 * if they are missing the app falls back to the system sans-serif, so it never crashes.
 */
object FontLoader {
    private const val LATO_REGULAR = "fonts/Lato-Regular.ttf"
    private const val LATO_BOLD = "fonts/Lato-Bold.ttf"
    private const val BN_SERIF = "fonts/NotoSerifBengali.ttf"

    private fun exists(am: AssetManager, path: String): Boolean =
        try { am.open(path).close(); true } catch (e: Exception) { false }

    fun load(context: Context): EcFonts {
        val am = context.assets
        val hasLato = exists(am, LATO_REGULAR) && exists(am, LATO_BOLD)
        val hasBn = exists(am, BN_SERIF)

        if (Build.VERSION.SDK_INT >= 29 && hasLato) {
            val reg = platformTypeface(am, LATO_REGULAR, if (hasBn) BN_SERIF else null, 400)
            val bold = platformTypeface(am, LATO_BOLD, if (hasBn) BN_SERIF else null, 700)
            if (reg != null && bold != null) return EcFonts(FontFamily(reg), FontFamily(bold))
        }
        if (hasLato) {
            return EcFonts(
                FontFamily(Font(LATO_REGULAR, am, FontWeight.Normal)),
                FontFamily(Font(LATO_BOLD, am, FontWeight.Bold)),
            )
        }
        if (hasBn) {
            val f = FontFamily(Font(BN_SERIF, am, FontWeight.Normal))
            return EcFonts(f, f)
        }
        return EcFonts(FontFamily.SansSerif, FontFamily.SansSerif)
    }

    /** Lato leads for Latin glyphs; Bengali glyphs fall through to Noto Serif Bengali, like the CSS font stack. */
    @RequiresApi(29)
    private fun platformTypeface(am: AssetManager, latoPath: String, bnPath: String?, bnWeight: Int): Typeface? = try {
        val lato = android.graphics.fonts.Font.Builder(am, latoPath).build()
        val latoFamily = android.graphics.fonts.FontFamily.Builder(lato).build()
        val builder = Typeface.CustomFallbackBuilder(latoFamily)
        if (bnPath != null) {
            val bn = android.graphics.fonts.Font.Builder(am, bnPath)
                .setFontVariationSettings("'wght' $bnWeight")
                .build()
            builder.addCustomFallback(android.graphics.fonts.FontFamily.Builder(bn).build())
        }
        builder.setSystemFallback("sans-serif")
        builder.build()
    } catch (e: Exception) {
        null
    }
}
