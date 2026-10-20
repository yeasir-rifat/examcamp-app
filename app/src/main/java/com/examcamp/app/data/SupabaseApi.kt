package com.examcamp.app.data

import com.examcamp.app.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Mirror of public.user_data: one row per Firebase uid, three jsonb columns. */
data class CloudDoc(
    val aiConfig: JsonElement?,
    val examPrefs: JsonElement?,
    val examHistory: JsonElement?,
)

/** Minimal PostgREST client. The website talks to Supabase with the same public anon key. */
object SupabaseApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val base = Config.SUPABASE_URL.toHttpUrl()

    private fun JsonElement?.orNull(): JsonElement? = if (this == null || this is JsonNull) null else this

    private fun nowIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    private fun Request.Builder.withKey(): Request.Builder =
        header("apikey", Config.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${Config.SUPABASE_ANON_KEY}")

    /**
     * Asks the `delete-account` Edge Function to erase this person's rows. The function checks the Firebase
     * ID token itself (see supabase/functions/delete-account in the website repo).
     */
    suspend fun deleteAccountData(idToken: String) {
        withContext(Dispatchers.IO) {
            val url = base.newBuilder().addPathSegments("functions/v1/delete-account").build()
            val body = buildJsonObject { put("idToken", idToken) }.toString().toRequestBody(jsonMedia)
            val request = Request.Builder().url(url).post(body).withKey().build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("delete-account ${resp.code}: ${resp.body?.string().orEmpty()}")
            }
        }
    }

    suspend fun fetchUserData(uid: String): CloudDoc? = withContext(Dispatchers.IO) {
        val url = base.newBuilder()
            .addPathSegments("rest/v1/user_data")
            .addQueryParameter("select", "ai_config,exam_prefs,exam_history")
            .addQueryParameter("firebase_uid", "eq.$uid")
            .addQueryParameter("limit", "1")
            .build()
        val request = Request.Builder().url(url).get().withKey().header("Accept", "application/json").build()
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Supabase ${resp.code}: $body")
            val row = json.parseToJsonElement(body).jsonArray.firstOrNull()?.jsonObject
                ?: return@use null
            CloudDoc(
                aiConfig = row["ai_config"].orNull(),
                examPrefs = row["exam_prefs"].orNull(),
                examHistory = row["exam_history"].orNull(),
            )
        }
    }

    /** Upserts only the columns present in [patch] (keys: ai_config, exam_prefs, exam_history). */
    suspend fun upsertUserData(uid: String, email: String?, patch: Map<String, JsonElement>) {
        withContext(Dispatchers.IO) {
            val row = buildJsonObject {
                put("firebase_uid", uid)
                put("updated_at", nowIso())
                if (email != null) put("email", email)
                patch.forEach { (key, value) -> put(key, value) }
            }
            val url = base.newBuilder()
                .addPathSegments("rest/v1/user_data")
                .addQueryParameter("on_conflict", "firebase_uid")
                .build()
            val request = Request.Builder()
                .url(url)
                .post(row.toString().toRequestBody(jsonMedia))
                .withKey()
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Supabase ${resp.code}: ${resp.body?.string().orEmpty()}")
            }
        }
    }

    // ---------- public.practice_progress: one row per (firebase_uid, subject_id, topic_id) ----------

    /** A row as the website writes it (flushPracticeProgressWrites in app.js). */
    data class PracticeProgressRow(
        val subjectId: String,
        val topicId: String,
        val subjectName: String?,
        val topicName: String?,
        val entry: PracticeEntry,
    )

    suspend fun fetchPracticeProgress(uid: String): List<PracticeProgressRow> = withContext(Dispatchers.IO) {
        val url = base.newBuilder()
            .addPathSegments("rest/v1/practice_progress")
            .addQueryParameter("select", "subject_id,topic_id,answers,current_index,answered_count,updated_at")
            .addQueryParameter("firebase_uid", "eq.$uid")
            .build()
        val request = Request.Builder().url(url).get().withKey().header("Accept", "application/json").build()
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Supabase ${resp.code}: $body")
            json.parseToJsonElement(body).jsonArray.mapNotNull { el ->
                val row = el as? JsonObject ?: return@mapNotNull null
                val subjectId = (row["subject_id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val topicId = (row["topic_id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val answers = (row["answers"] as? JsonObject)?.mapNotNull { (k, v) ->
                    (v as? JsonPrimitive)?.intOrNull?.let { k to it }
                }?.toMap().orEmpty()
                PracticeProgressRow(
                    subjectId = subjectId,
                    topicId = topicId,
                    subjectName = null,
                    topicName = null,
                    entry = PracticeEntry(
                        answers = answers,
                        current = (row["current_index"] as? JsonPrimitive)?.intOrNull ?: 0,
                        answeredCount = (row["answered_count"] as? JsonPrimitive)?.intOrNull ?: answers.size,
                        updatedAt = parseIso((row["updated_at"] as? JsonPrimitive)?.contentOrNull),
                    ),
                )
            }
        }
    }

    suspend fun upsertPracticeProgress(uid: String, rows: List<PracticeProgressRow>) {
        if (rows.isEmpty()) return
        withContext(Dispatchers.IO) {
            val payload = buildJsonArray {
                rows.forEach { r ->
                    add(buildJsonObject {
                        put("firebase_uid", uid)
                        put("subject_id", r.subjectId)
                        put("topic_id", r.topicId)
                        put("subject_name", r.subjectName)
                        put("topic_name", r.topicName)
                        put("answers", buildJsonObject { r.entry.answers.forEach { (k, v) -> put(k, v) } })
                        put("current_index", r.entry.current)
                        put("answered_count", r.entry.answeredCount)
                        put("updated_at", isoOf(if (r.entry.updatedAt > 0) r.entry.updatedAt else System.currentTimeMillis()))
                    })
                }
            }
            val url = base.newBuilder()
                .addPathSegments("rest/v1/practice_progress")
                .addQueryParameter("on_conflict", "firebase_uid,subject_id,topic_id")
                .build()
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMedia))
                .withKey()
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Supabase ${resp.code}: ${resp.body?.string().orEmpty()}")
            }
        }
    }

    private fun isoOf(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(ms))

    private val isoRegex = Regex("""^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d+))?(Z|[+-]\d{2}:?\d{2})?$""")

    /** Parses Postgres timestamptz text ("2026-10-19T04:41:00.123456+00:00" and friends) to epoch millis. minSdk 24, so no java.time. */
    fun parseIso(text: String?): Long {
        val m = isoRegex.matchEntire(text?.trim().orEmpty()) ?: return 0L
        val base = m.groupValues[1]
        val frac = (m.groupValues[2] + "000").take(3)
        var tz = m.groupValues[3]
        if (tz.isEmpty() || tz == "Z") tz = "+00:00"
        if (!tz.contains(":")) tz = tz.substring(0, 3) + ":" + tz.substring(3)
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).parse("$base.$frac$tz")?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
