package com.examcamp.app.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

enum class Lang(val code: String) {
    EN("en"), BN("bn");

    companion object {
        fun from(code: String?): Lang = if (code == "bn") BN else EN
    }
}

val LocalLang = compositionLocalOf { Lang.EN }

object I18n {
    fun get(lang: Lang, key: String): String {
        val primary = if (lang == Lang.BN) Dictionaries.bn else Dictionaries.en
        val extra = if (lang == Lang.BN) LiveDictionary.bn else LiveDictionary.en
        return primary[key] ?: extra[key] ?: Dictionaries.en[key] ?: key
    }
}

/** Looks up a website i18n key in the current interface language. */
@Composable
fun t(key: String): String = I18n.get(LocalLang.current, key)

/** Like [t] but fills %1$d / %2$s style placeholders. */
@Composable
fun tf(key: String, vararg args: Any): String = String.format(t(key), *args)
