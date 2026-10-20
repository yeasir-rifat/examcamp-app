package com.examcamp.app.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToLong

sealed interface GenState {
    data object Idle : GenState
    data object Loading : GenState
    data object Done : GenState
    /** [raw] is the internal error text (NO_API_KEY, GEMINI_HTTP_..., ...); the UI turns it into a message. */
    data class Error(val raw: String) : GenState
}

/**
 * Exam logic, ported from app.js: session model, answer / mark / navigate, scoring, and the
 * Generate flow. The active session and the exam history are saved the same way the website
 * does (history entry per sessionId, newest first, capped at 50) so both stay in sync.
 */
class ExamEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val store: LocalStore,
    private val namespace: StateFlow<String>,
    private val saveDoc: suspend (String, String) -> Unit,
    private val aiConfig: () -> AiConfig,
    /** Live Exam: fetches answer keys for a restored attempt. Null outside the Live Exam flow. */
    private val liveQuestions: (suspend (String) -> List<Question>?)? = null,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session

    private val _gen = MutableStateFlow<GenState>(GenState.Idle)
    val gen: StateFlow<GenState> = _gen

    private var lastConfig: ExamConfig? = null
    private var lastAttachments: List<Attachment> = emptyList()
    private var genJob: Job? = null
    private var persistJob: Job? = null
    private val persistMutex = Mutex()
    private var dirty = false

    init {
        scope.launch {
            namespace.collect { ns ->
                val restored = decode(store.blobOnce(ns, LocalStore.ACTIVE_SESSION))
                _session.value = restored
                if (restored != null && restored.needsLiveAnswers) hydrateLive(restored)
            }
        }
    }

    /** Live attempts are saved without answer keys (see forStorage). Put them back after a restart. */
    private suspend fun hydrateLive(restored: Session) {
        val fetch = liveQuestions ?: return
        val bank = fetch(restored.exam.liveExamId) ?: return
        _session.update { cur ->
            if (cur == null || cur.sessionId != restored.sessionId || !cur.needsLiveAnswers) cur
            else cur.copy(exam = cur.exam.copy(questions = cur.exam.questions.map { q ->
                val src = bank.firstOrNull { it.id == q.id }
                if (src == null) q else q.copy(correctAnswer = src.correctAnswer, explanation = src.explanation)
            }))
        }
    }

    /** A Live Exam attempt in progress is saved without its answer keys, like the website. */
    private fun forStorage(s: Session): Session =
        if (s.exam.liveExamId.isEmpty() || s.status != "active") s
        else s.copy(exam = s.exam.copy(questions = s.exam.questions.map { it.copy(correctAnswer = -1, explanation = "") }))

    private fun decode(raw: String?): Session? =
        if (raw.isNullOrBlank()) null
        else try {
            json.decodeFromString(Session.serializer(), raw).takeIf { it.exam.questions.isNotEmpty() }
        } catch (e: Exception) {
            null
        }

    // ---------- generate ----------

    fun generate(config: ExamConfig, attachments: List<Attachment>, sample: Boolean = false) {
        lastConfig = config
        lastAttachments = attachments
        runGeneration(sample)
    }

    fun retry(sample: Boolean = false) {
        if (lastConfig != null) runGeneration(sample)
    }

    fun cancelGeneration() {
        genJob?.cancel()
        _gen.value = GenState.Idle
    }

    fun resetGen() {
        _gen.value = GenState.Idle
    }

    private fun runGeneration(sample: Boolean) {
        val config = lastConfig ?: return
        genJob?.cancel()
        _gen.value = GenState.Loading
        genJob = scope.launch {
            try {
                val raw = if (sample) {
                    GeminiApi.sampleQuestions(config)
                } else {
                    val ai = aiConfig()
                    if (ai.apiKey.isBlank()) throw GeminiException("NO_API_KEY")
                    GeminiApi.generate(config, ai.apiKey, ai.model, readParts(lastAttachments))
                }
                val session = newSession(buildExam(config, raw))
                _session.value = session
                dirty = true
                persistNow(session)
                lastAttachments = emptyList()
                _gen.value = GenState.Done
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _gen.value = GenState.Error(e.message ?: "unknown")
            }
        }
    }

    private suspend fun readParts(list: List<Attachment>): List<InlinePart> = withContext(Dispatchers.IO) {
        list.mapNotNull { att ->
            val mime = when {
                att.mimeType in GeminiApi.SUPPORTED_MIME -> att.mimeType
                att.kind == Attachment.Kind.IMAGE -> "image/jpeg"
                else -> null // .doc/.docx/.ppt/.pptx: Gemini can't read them, same as the website
            } ?: return@mapNotNull null
            val bytes = context.contentResolver.openInputStream(att.uri)?.use { it.readBytes() } ?: return@mapNotNull null
            InlinePart(mime, Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
    }

    private fun genId(prefix: String): String {
        val rand = (1..6).map { "0123456789abcdefghijklmnopqrstuvwxyz".random() }.joinToString("")
        return "$prefix-${System.currentTimeMillis().toString(36)}-$rand"
    }

    private fun buildExam(config: ExamConfig, raw: List<RawQuestion>): Exam {
        val questions = raw.take(config.questionCount).mapIndexed { i, q ->
            Question(
                id = "question-${i + 1}",
                question = GeminiApi.autoWrapStrayLatex(q.question),
                options = q.options.map { GeminiApi.autoWrapStrayLatex(it) },
                correctAnswer = q.correctAnswer,
                explanation = GeminiApi.autoWrapStrayLatex(q.explanation),
            )
        }
        return Exam(
            examId = genId("exam"),
            subject = config.subject,
            topic = config.topic,
            prompt = config.prompt,
            language = GeminiApi.resolveLanguage(config),
            difficulty = config.difficulty,
            questionCount = questions.size,
            duration = config.duration,
            marksPerQuestion = 1.0,
            negativeMarking = config.negativeMarking,
            questions = questions,
        )
    }

    /** Starts a ready-made exam (Practice → Evaluate module) and saves it like a generated one. */
    fun startExam(exam: Exam) {
        val session = newSession(exam)
        _session.value = session
        dirty = true
        scope.launch { persistNow(session) }
    }

    fun newExamId(): String = genId("exam")

    private fun newSession(exam: Exam) = Session(
        sessionId = genId("session"),
        examId = exam.examId,
        status = "active",
        startedAt = System.currentTimeMillis(),
        exam = exam,
    )

    // ---------- taking the exam ----------

    fun remainingMs(s: Session): Long {
        val total = s.exam.limitMs
        return (total - (System.currentTimeMillis() - s.startedAt)).coerceIn(0, total)
    }

    fun selectAnswer(questionId: String, optionIndex: Int) = mutate { s ->
        s.copy(answers = s.answers + (questionId to optionIndex))
    }

    fun toggleMark(questionId: String) = mutate { s ->
        val marks = if (questionId in s.markedForReview) s.markedForReview - questionId else s.markedForReview + questionId
        s.copy(markedForReview = marks)
    }

    fun goTo(index: Int) = mutate { s ->
        s.copy(currentQuestion = index.coerceIn(0, (s.exam.questions.size - 1).coerceAtLeast(0)))
    }

    private fun mutate(change: (Session) -> Session) {
        var changed = false
        _session.update { s ->
            if (s == null || s.isCompleted) s else change(s).also { changed = true }
        }
        if (changed) {
            dirty = true
            schedulePersist()
        }
    }

    /** False when nothing was submitted: no active session, or a restored Live Exam whose answer keys are still loading. */
    fun submit(): Boolean {
        val s = _session.value ?: return false
        if (s.isCompleted || s.needsLiveAnswers) return false
        val done = s.copy(status = "completed", result = calculateResult(s))
        _session.value = done
        dirty = true
        persistJob?.cancel()
        scope.launch { persistNow(done) }
        return true
    }

    private fun calculateResult(s: Session): ExamResult = scoreSession(s, System.currentTimeMillis())

    // ---------- saving ----------

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(300)
            _session.value?.let { persistNow(it) }
        }
    }

    /** Called when the app goes to the background so the last answers are never lost. */
    fun flush() {
        persistJob?.cancel()
        val s = _session.value ?: return
        if (!dirty) return
        scope.launch { persistNow(s) }
    }

    private suspend fun persistNow(s: Session) = withContext(NonCancellable) {
        persistMutex.withLock {
            dirty = false
            val ns = namespace.value
            val stamped = forStorage(s).copy(updatedAt = System.currentTimeMillis())
            val text = json.encodeToString(Session.serializer(), stamped)
            store.setBlob(ns, LocalStore.ACTIVE_SESSION, text)

            val list = parseArray(store.blobOnce(ns, LocalStore.EXAM_HISTORY)).toMutableList()
            val entry = json.parseToJsonElement(text)
            val idx = list.indexOfFirst { sessionIdOf(it) == s.sessionId }
            if (idx == -1) list.add(0, entry) else list[idx] = entry
            saveDoc(LocalStore.EXAM_HISTORY, JsonArray(capHistory(list, HISTORY_MAX, System.currentTimeMillis())).toString())
        }
    }

    private fun parseArray(raw: String?): List<JsonElement> =
        if (raw.isNullOrBlank()) emptyList()
        else try { (json.parseToJsonElement(raw) as? JsonArray)?.toList().orEmpty() } catch (e: Exception) { emptyList() }

    private fun sessionIdOf(el: JsonElement): String? =
        ((el as? JsonObject)?.get("sessionId") as? JsonPrimitive)?.contentOrNull

    private fun updatedAtOf(el: JsonElement): Double =
        ((el as? JsonObject)?.get("updatedAt") as? JsonPrimitive)?.doubleOrNull ?: 0.0

    companion object {
        const val HISTORY_MAX = 50
    }
}
