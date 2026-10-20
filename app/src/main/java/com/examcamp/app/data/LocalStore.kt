package com.examcamp.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.examcamp.app.i18n.Lang
import com.examcamp.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "examcamp")

/**
 * Local source of truth, like localStorage on the website. Per-account data (AI config,
 * exam preferences, exam history) is namespaced by the Firebase uid, so one account's
 * cache can never show up in another account. Signed-out data lives under "guest".
 */
class LocalStore(private val context: Context) {

    data class Settings(val theme: ThemeMode, val lang: Lang)

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val LANG = stringPreferencesKey("lang")
    }

    val settings: Flow<Settings> = context.dataStore.data.map {
        Settings(ThemeMode.from(it[Keys.THEME]), Lang.from(it[Keys.LANG]))
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.code }
    }

    suspend fun setLang(lang: Lang) {
        context.dataStore.edit { it[Keys.LANG] = lang.code }
    }

    private fun blobKey(ns: String, name: String): Preferences.Key<String> =
        stringPreferencesKey("u:$ns:$name")

    fun blob(ns: String, name: String): Flow<String?> =
        context.dataStore.data.map { it[blobKey(ns, name)] }

    suspend fun blobOnce(ns: String, name: String): String? = blob(ns, name).first()

    suspend fun setBlob(ns: String, name: String, value: String?) {
        context.dataStore.edit {
            val key = blobKey(ns, name)
            if (value == null) it.remove(key) else it[key] = value
        }
    }

    /** Erases every document stored for one account (used by Delete account). */
    suspend fun clearNamespace(ns: String) {
        val prefix = "u:$ns:"
        context.dataStore.edit { prefs ->
            prefs.asMap().keys.filter { it.name.startsWith(prefix) }.forEach { prefs.remove(it) }
        }
    }

    companion object {
        const val GUEST = "guest"
        const val AI_CONFIG = "ai_config"
        const val EXAM_PREFS = "exam_prefs"
        const val EXAM_HISTORY = "exam_history"
        /** The exam in progress (or just finished). Local only, like the website. */
        const val ACTIVE_SESSION = "active_session"
        /** Names of documents changed on this phone that the cloud has not confirmed yet (survives a restart). */
        const val SYNC_DIRTY = "sync_dirty"
    }
}
