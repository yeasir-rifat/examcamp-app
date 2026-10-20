package com.examcamp.app.data

import com.examcamp.app.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Read-only client for the Practice Edge Function (same URLs the website calls, no auth for GET). */
object PracticeApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()
    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val base = Config.PRACTICE_API_BASE.toHttpUrl()

    private suspend fun getText(url: okhttp3.HttpUrl): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().header("Accept", "application/json").build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Practice API returned ${resp.code}")
            resp.body?.string().orEmpty()
        }
    }

    suspend fun fetchManifest(): List<PracticeCategory> {
        val url = base.newBuilder().addPathSegment("practice").addPathSegment("manifest").build()
        return parseManifest(json.parseToJsonElement(getText(url)))
    }

    suspend fun fetchTopicQuestions(subjectId: String, topicId: String): List<PracticeQuestion> {
        val url = base.newBuilder().addPathSegment("practice").addPathSegment(subjectId).addPathSegment(topicId).build()
        return parseQuestions(json.parseToJsonElement(getText(url)))
    }

    // ---------- parsing (accepts the same shapes fetchPracticeManifest / fetchPracticeTopicQuestions do) ----------

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull

    fun parseManifest(root: JsonElement): List<PracticeCategory> {
        val obj = root as? JsonObject ?: return emptyList()
        (obj["categories"] as? JsonArray)?.let { arr -> return arr.mapNotNull { parseCategory(it) } }
        // Legacy flat response: wrap into one default category.
        (obj["subjects"] as? JsonArray)?.let { arr ->
            return listOf(PracticeCategory("bcs-prelim", "বিসিএস প্রিলিমিনারী", "🏛️", "", arr.mapNotNull { parseSubject(it) }))
        }
        return emptyList()
    }

    private fun parseCategory(el: JsonElement): PracticeCategory? {
        val o = el as? JsonObject ?: return null
        val id = o["id"].str() ?: return null
        return PracticeCategory(
            id = id,
            name = o["name"].str().orEmpty(),
            icon = o["icon"].str().orEmpty().ifBlank { "🏛️" },
            description = o["description"].str().orEmpty(),
            subjects = (o["subjects"] as? JsonArray)?.mapNotNull { parseSubject(it) }.orEmpty(),
        )
    }

    private fun parseSubject(el: JsonElement): PracticeSubject? {
        val o = el as? JsonObject ?: return null
        val id = o["id"].str() ?: return null
        val mains = (o["mainTopics"] as? JsonArray)?.mapNotNull { m ->
            val mo = m as? JsonObject ?: return@mapNotNull null
            val mid = mo["id"].str() ?: return@mapNotNull null
            PracticeMainTopic(mid, mo["name"].str().orEmpty())
        }.orEmpty()
        val topics = (o["topics"] as? JsonArray)?.mapNotNull { t ->
            val to = t as? JsonObject ?: return@mapNotNull null
            val tid = to["id"].str() ?: return@mapNotNull null
            PracticeTopic(
                id = tid,
                name = to["name"].str().orEmpty(),
                language = to["language"].str()?.takeIf { it.isNotBlank() } ?: "en",
                mainTopicId = to["mainTopicId"].str()?.takeIf { it.isNotBlank() },
            )
        }.orEmpty()
        return PracticeSubject(id, o["name"].str().orEmpty(), mains, topics)
    }

    /** Response is {questions:[...], language?}; every question gets a stable id if the bank left it out. */
    fun parseQuestions(root: JsonElement): List<PracticeQuestion> {
        val arr = ((root as? JsonObject)?.get("questions") ?: root) as? JsonArray ?: return emptyList()
        val list = json.decodeFromJsonElement(ListSerializer(PracticeQuestion.serializer()), arr)
        return list
            .filter { it.options.isNotEmpty() }
            .mapIndexed { i, q -> if (q.id.isBlank()) q.copy(id = "q${i + 1}") else q }
    }

    // ---------- admin writes (the Worker checks the Firebase ID token and admin email) ----------

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private suspend fun send(req: Request.Builder, token: String, method: String, body: String?) = withContext(Dispatchers.IO) {
        val rb = body?.toRequestBody(jsonMedia)
        val request = req.method(method, rb).header("Authorization", "Bearer $token").build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Practice API returned ${resp.code}")
        }
    }

    /** PUT /practice/manifest: replaces the whole category/subject/topic tree (same slim body as pushPracticeManifest). */
    suspend fun putManifest(categories: List<PracticeCategory>, idToken: String) {
        val body = buildJsonObject {
            put("categories", buildJsonArray {
                categories.forEach { c ->
                    add(buildJsonObject {
                        put("id", c.id)
                        put("name", c.name)
                        put("icon", c.icon)
                        put("subjects", buildJsonArray {
                            c.subjects.forEach { s ->
                                add(buildJsonObject {
                                    put("id", s.id)
                                    put("name", s.name)
                                    put("mainTopics", buildJsonArray {
                                        s.mainTopics.forEach { m -> add(buildJsonObject { put("id", m.id); put("name", m.name) }) }
                                    })
                                    put("topics", buildJsonArray {
                                        s.topics.forEach { tp ->
                                            add(buildJsonObject {
                                                put("id", tp.id)
                                                put("name", tp.name)
                                                put("language", tp.language)
                                                put("mainTopicId", tp.mainTopicId)
                                            })
                                        }
                                    })
                                })
                            }
                        })
                    })
                }
            })
        }
        send(base.newBuilder().addPathSegment("practice").addPathSegment("manifest").build().let { Request.Builder().url(it) }, idToken, "PUT", body.toString())
    }

    /** PUT /practice/{subject}/{topic}: replaces one topic's question bank (pushPracticeTopicQuestions). */
    suspend fun putTopicQuestions(subjectId: String, topicId: String, questions: List<PracticeQuestion>, language: String, idToken: String) {
        val body = buildJsonObject {
            put("questions", buildJsonArray {
                questions.forEach { q ->
                    add(buildJsonObject {
                        put("id", q.id)
                        put("question", q.question)
                        put("options", buildJsonArray { q.options.forEach { add(JsonPrimitive(it)) } })
                        put("correctAnswer", q.correctAnswer)
                        put("explanation", q.explanation)
                        if (q.subtopic.isNotBlank()) put("subtopic", q.subtopic)
                    })
                }
            })
            put("language", language)
        }
        val url = base.newBuilder().addPathSegment("practice").addPathSegment(subjectId).addPathSegment(topicId).build()
        send(Request.Builder().url(url), idToken, "PUT", body.toString())
    }

    /** DELETE /practice/{subject}/{topic}: removes one topic's question bank. */
    suspend fun deleteTopicQuestions(subjectId: String, topicId: String, idToken: String) {
        val url = base.newBuilder().addPathSegment("practice").addPathSegment(subjectId).addPathSegment(topicId).build()
        send(Request.Builder().url(url), idToken, "DELETE", null)
    }
}
