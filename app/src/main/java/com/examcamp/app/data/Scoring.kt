package com.examcamp.app.data

import kotlin.math.roundToLong

/**
 * Port of getQuestionReviewState + calculateMarks + calculateResult from the website, kept free of Android
 * and of the clock so it can be unit tested. [now] is the submit time in epoch milliseconds.
 */
fun scoreSession(s: Session, now: Long): ExamResult {
    val exam = s.exam
    val marksPer = exam.marksPerQuestion
    var correct = 0
    var wrong = 0
    var unanswered = 0
    for (q in exam.questions) {
        val sel = s.answers[q.id]
        when {
            sel == null -> unanswered++
            sel == q.correctAnswer -> correct++
            else -> wrong++
        }
    }
    val obtained = ((correct * marksPer - wrong * exam.negativeMarking) * 100).roundToLong() / 100.0
    val total = exam.questions.size * marksPer
    val pct = if (total == 0.0) 0.0 else (obtained / total * 10000).roundToLong() / 100.0
    val durationMs = exam.limitMs
    val used = (now - s.startedAt).coerceIn(0, durationMs)
    return ExamResult(
        examId = exam.examId,
        sessionId = s.sessionId,
        submittedAt = now,
        totalQuestions = exam.questions.size,
        attempted = exam.questions.size - unanswered,
        correct = correct,
        wrong = wrong,
        unanswered = unanswered,
        totalMarks = total,
        obtainedMarks = obtained,
        percentage = pct,
        timeAllowed = durationMs,
        timeUsed = used,
        timeRemaining = durationMs - used,
        answers = s.answers,
    )
}
