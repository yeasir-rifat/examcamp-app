package com.examcamp.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Practice Admin writes (renderPracticeAdmin* and pushPracticeManifest / pushPracticeTopicQuestions in app.js).
 * The manifest is one resource: every structural change loads nothing new, edits a copy of the tree, and PUTs the whole tree back.
 */
class PracticeAdminEngine(
    private val scope: CoroutineScope,
    private val practice: PracticeEngine,
    private val idToken: suspend () -> String?,
) {
    fun newId(prefix: String): String = "$prefix-${System.currentTimeMillis()}"

    private suspend fun withToken(block: suspend (String) -> Unit): String? {
        val token = runCatching { idToken() }.getOrNull() ?: return "Sign in again."
        return try {
            block(token)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.message ?: "Request failed"
        }
    }

    suspend fun loadTree(): List<PracticeCategory> = PracticeApi.fetchManifest()

    /** Saves the whole tree, then reloads the Practice panel so the change shows at once. */
    suspend fun saveTree(tree: List<PracticeCategory>): String? {
        val err = withToken { PracticeApi.putManifest(tree, it) }
        if (err == null) practice.refreshManifest()
        return err
    }

    suspend fun loadQuestions(subjectId: String, topicId: String): List<PracticeQuestion> =
        runCatching { PracticeApi.fetchTopicQuestions(subjectId, topicId) }.getOrDefault(emptyList())

    suspend fun saveQuestions(subjectId: String, topicId: String, language: String, questions: List<PracticeQuestion>): String? =
        withToken { PracticeApi.putTopicQuestions(subjectId, topicId, questions, language, it) }

    suspend fun deleteQuestions(subjectId: String, topicId: String): String? =
        withToken { PracticeApi.deleteTopicQuestions(subjectId, topicId, it) }
}

/** Pure edits on the category tree. Each returns a new tree; nothing is saved here. */
object PracticeTree {
    fun addCategory(t: List<PracticeCategory>, id: String, name: String, icon: String) =
        t + PracticeCategory(id = id, name = name, icon = icon)

    fun updateCategory(t: List<PracticeCategory>, catId: String, name: String, icon: String) =
        t.map { if (it.id == catId) it.copy(name = name, icon = icon) else it }

    fun deleteCategory(t: List<PracticeCategory>, catId: String) = t.filter { it.id != catId }

    fun addSubject(t: List<PracticeCategory>, catId: String, id: String, name: String) =
        t.map { if (it.id == catId) it.copy(subjects = it.subjects + PracticeSubject(id = id, name = name)) else it }

    fun updateSubject(t: List<PracticeCategory>, catId: String, subId: String, name: String) =
        mapSubject(t, catId) { if (it.id == subId) it.copy(name = name) else it }

    fun deleteSubject(t: List<PracticeCategory>, catId: String, subId: String) =
        mapSubject(t, catId) { s -> s }.let { list -> list.map { c -> if (c.id == catId) c.copy(subjects = c.subjects.filter { it.id != subId }) else c } }

    fun addTopic(t: List<PracticeCategory>, catId: String, subId: String, id: String, name: String, language: String) =
        mapSubject(t, catId) { s ->
            if (s.id == subId) s.copy(topics = s.topics + PracticeTopic(id = id, name = name, language = language)) else s
        }

    fun updateTopic(t: List<PracticeCategory>, catId: String, subId: String, topicId: String, name: String, language: String) =
        mapSubject(t, catId) { s ->
            if (s.id != subId) s
            else s.copy(topics = s.topics.map { if (it.id == topicId) it.copy(name = name, language = language) else it })
        }

    fun deleteTopic(t: List<PracticeCategory>, catId: String, subId: String, topicId: String) =
        mapSubject(t, catId) { s ->
            if (s.id != subId) s else s.copy(topics = s.topics.filter { it.id != topicId })
        }

    private fun mapSubject(t: List<PracticeCategory>, catId: String, f: (PracticeSubject) -> PracticeSubject) =
        t.map { c -> if (c.id == catId) c.copy(subjects = c.subjects.map(f)) else c }
}

/**
 * Parses the question JSON of a Practice topic: {question, options (4), correctAnswer (0-3), explanation, subtopic?}.
 * The ids of existing questions are kept by position, so saved progress still matches. Throws IllegalArgumentException.
 */
fun parsePracticeQuestions(raw: String, existing: List<PracticeQuestion>): List<PracticeQuestion> {
    val base = parseQuestionBank(raw)
    val arr = Json.parseToJsonElement(raw.trim()) as JsonArray
    return base.mapIndexed { i, q ->
        val subtopic = (arr[i] as? JsonObject)?.get("subtopic")?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        PracticeQuestion(
            id = existing.getOrNull(i)?.id?.takeIf { it.isNotBlank() } ?: "q-${System.currentTimeMillis()}-$i",
            question = q.question,
            options = q.options,
            correctAnswer = q.correctAnswer,
            explanation = q.explanation,
            subtopic = subtopic,
        )
    }
}
