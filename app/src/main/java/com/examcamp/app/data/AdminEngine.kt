package com.examcamp.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Live Exam admin writes (the renderAdmin, save, delete and resolve functions in app.js).
 * Every write returns null on success or an error message. Successful writes reload the data.
 * Access is checked by the screen with LiveExamEngine.isLiveAdmin; RLS and the Worker check it again on the server.
 */
class AdminEngine(
    private val scope: CoroutineScope,
    private val live: LiveExamEngine,
    private val idToken: suspend () -> String?,
) {
    private val _pending = MutableStateFlow<List<EnrollmentRequest>>(emptyList())
    val pending: StateFlow<List<EnrollmentRequest>> = _pending

    fun reloadPending() {
        scope.launch {
            runCatching { LiveExamApi.fetchPendingEnrollments() }.onSuccess { _pending.value = it }
        }
    }

    private suspend fun write(block: suspend () -> Unit): String? =
        try {
            block()
            live.refresh()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.message ?: "Request failed"
        }

    fun newId(prefix: String): String = "$prefix-${System.currentTimeMillis()}"

    suspend fun addSubject(name: String): String? = write { LiveExamApi.upsertSubject(newId("sub"), name) }

    suspend fun renameSubject(id: String, name: String): String? = write { LiveExamApi.renameSubject(id, name) }

    suspend fun deleteSubject(id: String): String? = write { LiveExamApi.deleteSubject(id) }

    suspend fun saveExam(exam: LiveExam, questionCount: Int): String? = write { LiveExamApi.upsertExam(exam, questionCount) }

    suspend fun deleteExam(id: String): String? = write { LiveExamApi.deleteExam(id) }

    suspend fun setPublished(exam: LiveExam, published: Boolean): String? =
        write { LiveExamApi.upsertExam(exam.copy(published = published), exam.questionCount) }

    /** Publish or unpublish every exam of one subject at once (renderAdminResultsList subject toggle). */
    suspend fun setSubjectPublished(exams: List<LiveExam>, published: Boolean): String? = write {
        exams.forEach { LiveExamApi.upsertExam(it.copy(published = published), it.questionCount) }
    }

    suspend fun saveMember(member: LiveMember): String? = write { LiveExamApi.upsertMember(member) }

    suspend fun deleteMember(email: String): String? = write { LiveExamApi.deleteMember(email) }

    /** Approve: merge the courses into the member (create it if new), then mark the request approved. */
    suspend fun approve(req: EnrollmentRequest): String? {
        val existing = live.members.value.firstOrNull { it.email == req.email }
        val merged = LiveMember(
            email = req.email,
            name = existing?.name?.takeIf { it.isNotBlank() } ?: req.name,
            role = existing?.role?.takeIf { it.isNotBlank() } ?: "subscriber",
            subjects = ((existing?.subjects.orEmpty()) + req.courses).distinct(),
        )
        val err = write {
            LiveExamApi.upsertMember(merged)
            LiveExamApi.setEnrollmentStatus(req.id, "approved")
        }
        if (err == null) _pending.value = _pending.value.filter { it.id != req.id }
        return err
    }

    suspend fun dismiss(req: EnrollmentRequest): String? {
        val err = write { LiveExamApi.setEnrollmentStatus(req.id, "dismissed") }
        if (err == null) _pending.value = _pending.value.filter { it.id != req.id }
        return err
    }

    /** Replaces the question bank of an exam, then stores the new question count on the exam. */
    suspend fun saveQuestions(exam: LiveExam, questions: List<Question>): String? {
        val token = runCatching { idToken() }.getOrNull() ?: return "Sign in again to save questions."
        return write {
            LiveExamApi.putQuestions(exam.id, token, questions)
            LiveExamApi.upsertExam(exam, questions.size)
        }
    }
}

/**
 * Parses the question JSON the admin pastes: an array of {question, options (4 strings), correctAnswer (0-3), explanation}.
 * Throws IllegalArgumentException with a message that says which question is wrong.
 */
fun parseQuestionBank(raw: String): List<Question> {
    val json = Json { ignoreUnknownKeys = true }
    val root = try {
        json.parseToJsonElement(raw.trim())
    } catch (e: Exception) {
        throw IllegalArgumentException("The text is not valid JSON.")
    }
    val arr = root as? JsonArray ?: throw IllegalArgumentException("The JSON must be an array of questions.")
    if (arr.isEmpty()) throw IllegalArgumentException("The array has no questions.")
    return arr.mapIndexed { i, el ->
        val o = el as? JsonObject ?: throw IllegalArgumentException("Question ${i + 1} is not an object.")
        val q = (o["question"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Question ${i + 1} has no question text.")
        val options = (o["options"] as? JsonArray)?.map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }.orEmpty()
        if (options.size != 4 || options.any { it.isBlank() }) {
            throw IllegalArgumentException("Question ${i + 1} needs exactly 4 non-empty options.")
        }
        val correct = (o["correctAnswer"] as? JsonPrimitive)?.intOrNull
            ?: throw IllegalArgumentException("Question ${i + 1} needs correctAnswer (0 to 3).")
        if (correct !in 0..3) throw IllegalArgumentException("Question ${i + 1}: correctAnswer must be 0 to 3.")
        Question(
            id = "question-${i + 1}",
            question = q,
            options = options,
            correctAnswer = correct,
            explanation = (o["explanation"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
        )
    }
}
