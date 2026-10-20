package com.examcamp.app.data

import kotlinx.serialization.Serializable

/** A Subject Campaign (public.subjects). */
@Serializable
data class LiveSubject(val id: String, val name: String)

/** A scheduled Live Exam (public.live_exams). Times are epoch milliseconds. */
@Serializable
data class LiveExam(
    val id: String,
    val subject: String,
    val topic: String,
    val language: String,
    val startMs: Long,
    val durationMinutes: Int,
    val marksPerQuestion: Double,
    val negativeMarking: Double,
    val status: String,
    val published: Boolean,
    val questionCount: Int,
) {
    val endMs: Long get() = startMs + durationMinutes * 60_000L
    fun isLiveAt(now: Long): Boolean = now >= startMs && now < endMs
    fun isOver(now: Long): Boolean = now >= endMs
}

/** A row of public.members. Admins and assistant admins have access to every subject. */
@Serializable
data class LiveMember(val email: String, val name: String, val role: String, val subjects: List<String>)

/** A row of public.live_exam_submissions, reduced to what Merit and the summary need. */
@Serializable
data class LiveSubmission(val examId: String, val email: String, val name: String, val scorePercent: Double)

/** One line of a merit list. There is one list per live exam (and so per course), so every row carries its exam id. */
data class MeritRow(val examId: String, val email: String, val name: String, val score: Double, val self: Boolean)

/** The three numbers at the top of the Live Exam screen. */
data class LiveSummary(val rank: Int = 0, val given: Int = 0, val avgPercent: Double = 0.0)

/** Payload of the public "Enroll Now" form (insert into public.enrollment_requests). */
data class EnrollmentDraft(
    val name: String,
    val phone: String,
    val email: String,
    val courses: List<String>,
    val mfsProvider: String,
    val mfsTransactionId: String,
)

/** Outcome of trying to start a live exam. */
sealed interface JoinResult {
    data class Ready(val exam: LiveExam, val questions: List<Question>) : JoinResult
    data object NotEnrolled : JoinResult
    data object NotLive : JoinResult
    data object AlreadySubmitted : JoinResult
    data object NoQuestions : JoinResult
    data class Failed(val message: String) : JoinResult
}

/** The enrollment form as saved on the device while the member fills it in (cleared after a successful send). */
@kotlinx.serialization.Serializable
data class EnrollFormState(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val txn: String = "",
    val provider: String = "Bkash",
    val courses: List<String> = emptyList(),
)

/** A row of public.enrollment_requests that is still pending. */
data class EnrollmentRequest(
    val id: String,
    val name: String,
    val phone: String,
    val email: String,
    val courses: List<String>,
    val mfsProvider: String,
    val txn: String,
    val submittedAt: Long,
)
