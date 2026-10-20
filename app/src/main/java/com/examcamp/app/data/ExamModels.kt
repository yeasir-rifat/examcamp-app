package com.examcamp.app.data

import kotlinx.serialization.Serializable

/**
 * These shapes match the website's exam session JSON exactly (see createExamSession /
 * calculateResult in app.js), so a session saved here shows up correctly on the website
 * through the Supabase `user_data.exam_history` sync, and the other way round.
 */
@Serializable
data class Question(
    val id: String,
    val question: String,
    val options: List<String>,
    val correctAnswer: Int,
    val explanation: String = "",
)

@Serializable
data class Exam(
    val examId: String,
    val subject: String = "",
    val topic: String = "",
    val prompt: String = "",
    val language: String = "en",
    val difficulty: String = "medium",
    val questionCount: Int = 0,
    val duration: Int = 60,
    val marksPerQuestion: Double = 1.0,
    val negativeMarking: Double = 0.0,
    val additionalInstructions: String = "",
    /** Set for Practice → Evaluate attempts: "subjectId::topicId::module-N" (same field as the website). */
    val practiceEvalId: String = "",
    val questions: List<Question> = emptyList(),
    /** Live Exam only: the live_exams id. Empty for every other exam. */
    val liveExamId: String = "",
    /** Live Exam only: time left until the shared deadline when the member joined (ms). 0 = use duration. */
    val timeLimitMs: Long = 0,
) {
    /** Total time for this attempt in ms. */
    val limitMs: Long get() = if (timeLimitMs > 0) timeLimitMs else duration * 60_000L
}

@Serializable
data class ExamResult(
    val examId: String = "",
    val sessionId: String = "",
    val submittedAt: Long = 0,
    val totalQuestions: Int = 0,
    val attempted: Int = 0,
    val correct: Int = 0,
    val wrong: Int = 0,
    val unanswered: Int = 0,
    val totalMarks: Double = 0.0,
    val obtainedMarks: Double = 0.0,
    val percentage: Double = 0.0,
    val timeAllowed: Long = 0,
    val timeUsed: Long = 0,
    val timeRemaining: Long = 0,
    val answers: Map<String, Int> = emptyMap(),
)

@Serializable
data class Session(
    val sessionId: String,
    val examId: String,
    val status: String = "active", // "active" | "completed"
    val startedAt: Long,
    val currentQuestion: Int = 0,
    val answers: Map<String, Int> = emptyMap(),
    val markedForReview: List<String> = emptyList(),
    val exam: Exam,
    val result: ExamResult? = null,
    val updatedAt: Long = 0,
) {
    val isCompleted: Boolean get() = status == "completed"
    /** A restored Live Exam attempt whose answer keys have not been fetched yet. */
    val needsLiveAnswers: Boolean get() = exam.liveExamId.isNotEmpty() && exam.questions.any { it.correctAnswer < 0 }
}

/** What the AI Mode form collects (readAiModeFormConfig in app.js). */
data class ExamConfig(
    val prompt: String,
    val subject: String,
    val topic: String,
    val questionCount: Int,
    val difficulty: String,
    val language: String, // "en" | "bn" | "en-bn"
    val duration: Int,
    val negativeMarking: Double,
    val attachmentCount: Int,
)

/** One captured or uploaded source file for the AI request. */
data class Attachment(
    val id: String,
    val uri: android.net.Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
) {
    val kind: Kind
        get() = when {
            mimeType.startsWith("image/") -> Kind.IMAGE
            mimeType == "application/pdf" || name.endsWith(".pdf", true) -> Kind.PDF
            else -> Kind.OTHER
        }

    enum class Kind { IMAGE, PDF, OTHER }
}

/** Settings → AI Configuration (website key: mcq-exam-ai-config). */
@Serializable
data class AiConfig(val apiKey: String = "", val model: String = "")

/** Settings → Exam Preferences (website key: mcq-exam-preferences). */
@Serializable
data class ExamPrefs(
    val difficulty: String = "medium",
    val language: String = "en-bn",
    val duration: String = "60",
    val autosave: Boolean = true,
    val showAnswerKey: Boolean = true,
    val negativeMarkEnabled: Boolean = true,
    val negativeMarkValue: Double = 0.25,
)

/**
 * Stable Gemini API models as listed on ai.google.dev/gemini-api/docs/models (checked 2026-10-20).
 * Retired or restricted ids that older versions offered (gemini-3.5-flash, gemini-3.1-pro-preview,
 * gemini-2.5-*) are not listed; [resolve] maps a saved one to [DEFAULT] so generation never breaks.
 */
object GeminiModels {
    const val DEFAULT = "gemini-3.8-flash"
    val options = listOf(
        "gemini-3.8-flash" to "Gemini 3.8 Flash (Recommended)",
        "gemini-4-argon" to "Gemini 4 Argon",
        "gemini-3.6-flash" to "Gemini 3.6 Flash",
        "gemini-3.5-flash-lite" to "Gemini 3.5 Flash-Lite",
        "gemini-3.1-flash-lite" to "Gemini 3.1 Flash-Lite",
    )

    /** The model id to call: the saved one if it is still offered, otherwise [DEFAULT]. */
    fun resolve(saved: String): String =
        if (options.any { it.first == saved }) saved else DEFAULT
}
