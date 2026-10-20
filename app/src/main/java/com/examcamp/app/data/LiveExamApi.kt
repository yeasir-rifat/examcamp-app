package com.examcamp.app.data

import com.examcamp.app.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Live Exam reads and writes. Same tables, same anon key and same RLS as the website (app.js).
 * Question banks are NOT read from the tables: they come from the Practice/Live Worker route,
 * which checks the Firebase ID token and the exam's live window (see fetchQuestions).
 */
object LiveExamApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val supabase = Config.SUPABASE_URL.toHttpUrl()
    private val practice = Config.PRACTICE_API_BASE.toHttpUrl()

    private fun Request.Builder.withKey(): Request.Builder =
        header("apikey", Config.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${Config.SUPABASE_ANON_KEY}")

    private fun table(name: String, select: String, order: String? = null): HttpUrl {
        val b = supabase.newBuilder()
            .addPathSegments("rest/v1/$name")
            .addQueryParameter("select", select)
        if (order != null) b.addQueryParameter("order", order)
        return b.build()
    }

    private suspend fun getRows(url: HttpUrl): List<JsonObject> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).get().withKey().header("Accept", "application/json").build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Supabase ${resp.code}: $body")
            json.parseToJsonElement(body).jsonArray.mapNotNull { it as? JsonObject }
        }
    }

    private suspend fun post(url: HttpUrl, payload: JsonElement, prefer: String) = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMedia))
            .withKey()
            .header("Prefer", prefer)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Supabase ${resp.code}: ${resp.body?.string().orEmpty()}")
        }
    }

    private fun JsonObject.s(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.i(key: String): Int = s(key)?.toIntOrNull() ?: 0
    private fun JsonObject.d(key: String): Double = s(key)?.toDoubleOrNull() ?: 0.0
    private fun JsonObject.b(key: String): Boolean = s(key) == "true"
    private fun JsonObject.strings(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

    suspend fun fetchSubjects(): List<LiveSubject> =
        getRows(table("subjects", "*", "created_at")).mapNotNull { o ->
            LiveSubject(id = o.s("id") ?: return@mapNotNull null, name = o.s("name").orEmpty())
        }

    suspend fun fetchExams(): List<LiveExam> =
        getRows(table("live_exams", "*", "start_at")).mapNotNull { o ->
            LiveExam(
                id = o.s("id") ?: return@mapNotNull null,
                subject = o.s("subject").orEmpty(),
                topic = o.s("topic").orEmpty(),
                language = o.s("language") ?: "en",
                startMs = SupabaseApi.parseIso(o.s("start_at")),
                durationMinutes = o.i("duration_minutes"),
                marksPerQuestion = o.d("marks_per_question").takeIf { it > 0 } ?: 1.0,
                negativeMarking = o.d("negative_marking"),
                status = o.s("status") ?: "scheduled",
                published = o.b("published"),
                questionCount = o.i("question_count"),
            )
        }

    suspend fun fetchMembers(): List<LiveMember> =
        getRows(table("members", "*")).mapNotNull { o ->
            LiveMember(
                email = (o.s("email") ?: return@mapNotNull null).lowercase(),
                name = o.s("name").orEmpty(),
                role = o.s("role").orEmpty(),
                subjects = o.strings("subjects"),
            )
        }

    suspend fun fetchSubmissions(): List<LiveSubmission> =
        getRows(table("live_exam_submissions", "exam_id,member_email,member_name,score_percent")).mapNotNull { o ->
            LiveSubmission(
                examId = o.s("exam_id") ?: return@mapNotNull null,
                email = (o.s("member_email") ?: return@mapNotNull null).lowercase(),
                name = o.s("member_name").orEmpty(),
                scorePercent = o.d("score_percent"),
            )
        }

    /** Same upsert as submitLiveExamResultToSupabase() on the website: one row per exam and member. */
    suspend fun upsertSubmission(
        examId: String,
        email: String,
        name: String,
        answers: Map<String, Int>,
        obtained: Double,
        total: Double,
    ) {
        val percent = if (total > 0) Math.round(obtained / total * 1000) / 10.0 else 0.0
        val row = buildJsonObject {
            put("exam_id", examId)
            put("member_email", email)
            put("member_name", name)
            put("answers", buildJsonObject { answers.forEach { (k, v) -> put(k, v) } })
            put("score_percent", percent)
            put("obtained_marks", obtained)
            put("total_marks", total)
        }
        val url = supabase.newBuilder()
            .addPathSegments("rest/v1/live_exam_submissions")
            .addQueryParameter("on_conflict", "exam_id,member_email")
            .build()
        post(url, JsonArray(listOf(row)), "resolution=merge-duplicates,return=minimal")
    }

    /** Same insert as submitEnrollmentToSupabase() on the website. */
    suspend fun insertEnrollment(d: EnrollmentDraft) {
        val row = buildJsonObject {
            put("name", d.name)
            put("phone", d.phone)
            put("email", d.email)
            put("courses", buildJsonArray { d.courses.forEach { add(JsonPrimitive(it)) } })
            put("mfs_provider", d.mfsProvider)
            put("mfs_transaction_id", d.mfsTransactionId)
        }
        val url = supabase.newBuilder().addPathSegments("rest/v1/enrollment_requests").build()
        post(url, row, "return=minimal")
    }

    /**
     * GET {PRACTICE_API_BASE}/live-exam/{id}/questions with the Firebase ID token, like
     * fetchExamQuestionBank() on the website. The Worker refuses it outside the live window or
     * without subject access, so correct answers only reach a member who is taking that exam.
     */
    suspend fun fetchQuestions(examId: String, idToken: String): List<Question> = withContext(Dispatchers.IO) {
        val url = practice.newBuilder()
            .addPathSegment("live-exam")
            .addPathSegment(examId)
            .addPathSegment("questions")
            .build()
        val req = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $idToken")
            .header("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = (runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull())?.s("error")
                throw IOException(msg ?: "HTTP ${resp.code}")
            }
            json.parseToJsonElement(body).jsonArray
                .mapNotNull { it as? JsonObject }
                .mapIndexed { i, o ->
                    Question(
                        id = "question-${i + 1}",
                        question = GeminiApi.autoWrapStrayLatex(o.s("question_html").orEmpty()),
                        options = o.strings("options").map { GeminiApi.autoWrapStrayLatex(it) },
                        correctAnswer = o.i("correct_answer"),
                        explanation = GeminiApi.autoWrapStrayLatex(o.s("explanation_html").orEmpty()),
                    )
                }
        }
    }

    // ---------- admin writes (same tables and calls as the website's admin functions) ----------

    private fun tableUrl(name: String, vararg query: Pair<String, String>): HttpUrl {
        val b = supabase.newBuilder().addPathSegments("rest/v1/$name")
        query.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build()
    }

    private suspend fun write(url: HttpUrl, method: String, body: JsonElement?, prefer: String = "return=minimal") =
        withContext(Dispatchers.IO) {
            val reqBody = body?.toString()?.toRequestBody(jsonMedia)
            val req = Request.Builder().url(url).method(method, reqBody).withKey().header("Prefer", prefer).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Supabase ${resp.code}: ${resp.body?.string().orEmpty()}")
            }
        }

    /** Supabase timestamps are UTC ISO strings. */
    fun isoUtc(ms: Long): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(java.util.Date(ms))
    }

    suspend fun upsertSubject(id: String, name: String) {
        val row = buildJsonObject { put("id", id); put("name", name) }
        write(tableUrl("subjects", "on_conflict" to "id"), "POST", JsonArray(listOf(row)), "resolution=merge-duplicates,return=minimal")
    }

    suspend fun renameSubject(id: String, name: String) =
        write(tableUrl("subjects", "id" to "eq.$id"), "PATCH", buildJsonObject { put("name", name) })

    suspend fun deleteSubject(id: String) = write(tableUrl("subjects", "id" to "eq.$id"), "DELETE", null)

    suspend fun upsertExam(e: LiveExam, questionCount: Int) {
        val row = buildJsonObject {
            put("id", e.id)
            put("subject", e.subject)
            put("topic", e.topic)
            put("language", e.language)
            put("start_at", isoUtc(e.startMs))
            put("duration_minutes", e.durationMinutes)
            put("marks_per_question", e.marksPerQuestion)
            put("negative_marking", e.negativeMarking)
            put("status", e.status)
            put("published", e.published)
            put("question_count", questionCount)
        }
        write(tableUrl("live_exams", "on_conflict" to "id"), "POST", JsonArray(listOf(row)), "resolution=merge-duplicates,return=minimal")
    }

    suspend fun deleteExam(id: String) = write(tableUrl("live_exams", "id" to "eq.$id"), "DELETE", null)

    suspend fun upsertMember(m: LiveMember) {
        val row = buildJsonObject {
            put("email", m.email)
            put("name", m.name)
            put("role", m.role)
            put("subjects", buildJsonArray { m.subjects.forEach { add(JsonPrimitive(it)) } })
        }
        write(tableUrl("members", "on_conflict" to "email"), "POST", JsonArray(listOf(row)), "resolution=merge-duplicates,return=minimal")
    }

    suspend fun deleteMember(email: String) = write(tableUrl("members", "email" to "eq.$email"), "DELETE", null)

    suspend fun setEnrollmentStatus(id: String, status: String) =
        write(tableUrl("enrollment_requests", "id" to "eq.$id"), "PATCH", buildJsonObject { put("status", status) })

    suspend fun fetchPendingEnrollments(): List<EnrollmentRequest> {
        val url = supabase.newBuilder()
            .addPathSegments("rest/v1/enrollment_requests")
            .addQueryParameter("select", "*")
            .addQueryParameter("status", "eq.pending")
            .addQueryParameter("order", "submitted_at")
            .build()
        return getRows(url).mapNotNull { o ->
            EnrollmentRequest(
                id = o.s("id") ?: return@mapNotNull null,
                name = o.s("name").orEmpty(),
                phone = o.s("phone").orEmpty(),
                email = (o.s("email") ?: "").lowercase(),
                courses = o.strings("courses"),
                mfsProvider = o.s("mfs_provider").orEmpty(),
                txn = o.s("mfs_transaction_id").orEmpty(),
                submittedAt = SupabaseApi.parseIso(o.s("submitted_at")),
            )
        }
    }

    /** Replaces the question bank of one exam through the Worker's admin-only PUT route. */
    suspend fun putQuestions(examId: String, idToken: String, questions: List<Question>) = withContext(Dispatchers.IO) {
        val url = practice.newBuilder()
            .addPathSegment("live-exam")
            .addPathSegment(examId)
            .addPathSegment("questions")
            .build()
        val body = buildJsonObject {
            put("questions", buildJsonArray {
                questions.forEach { q ->
                    add(buildJsonObject {
                        put("question", q.question)
                        put("options", buildJsonArray { q.options.forEach { add(JsonPrimitive(it)) } })
                        put("correctAnswer", q.correctAnswer)
                        put("explanation", q.explanation)
                    })
                }
            })
        }
        val req = Request.Builder()
            .url(url)
            .put(body.toString().toRequestBody(jsonMedia))
            .header("Authorization", "Bearer $idToken")
            .header("Content-Type", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val msg = (runCatching { json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject }.getOrNull())?.s("error")
                throw IOException(msg ?: "HTTP ${resp.code}")
            }
        }
    }
}
