package com.examcamp.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringTest {

    private fun exam(negative: Double, marks: Double = 1.0, count: Int = 5) = Exam(
        examId = "e1",
        duration = 10,
        marksPerQuestion = marks,
        negativeMarking = negative,
        questions = (1..count).map { Question(id = "q$it", question = "Q$it", options = listOf("a", "b", "c", "d"), correctAnswer = 1) },
    )

    private fun session(exam: Exam, answers: Map<String, Int>, startedAt: Long = 1_000L) =
        Session(sessionId = "s1", examId = exam.examId, startedAt = startedAt, answers = answers, exam = exam)

    @Test fun countsCorrectWrongAndUnanswered() {
        // q1,q2,q3 right (answer 1), q4 wrong, q5 unanswered
        val s = session(exam(0.25), mapOf("q1" to 1, "q2" to 1, "q3" to 1, "q4" to 0))
        val r = scoreSession(s, now = 61_000L)
        assertEquals(3, r.correct)
        assertEquals(1, r.wrong)
        assertEquals(1, r.unanswered)
        assertEquals(4, r.attempted)
        assertEquals(5, r.totalQuestions)
    }

    @Test fun negativeMarkingIsSubtracted() {
        val s = session(exam(0.25), mapOf("q1" to 1, "q2" to 1, "q3" to 1, "q4" to 0))
        val r = scoreSession(s, now = 61_000L)
        assertEquals(2.75, r.obtainedMarks, 1e-9)
        assertEquals(5.0, r.totalMarks, 1e-9)
        assertEquals(55.0, r.percentage, 1e-9)
    }

    @Test fun noNegativeMarkingMeansPlainCount() {
        val s = session(exam(0.0), mapOf("q1" to 1, "q2" to 0, "q3" to 0))
        val r = scoreSession(s, now = 5_000L)
        assertEquals(1.0, r.obtainedMarks, 1e-9)
        assertEquals(20.0, r.percentage, 1e-9)
    }

    @Test fun customMarksPerQuestion() {
        val s = session(exam(negative = 0.5, marks = 2.0), mapOf("q1" to 1, "q2" to 1, "q3" to 0))
        val r = scoreSession(s, now = 5_000L)
        assertEquals(3.5, r.obtainedMarks, 1e-9) // 2 right * 2 - 1 wrong * 0.5
        assertEquals(10.0, r.totalMarks, 1e-9)
        assertEquals(35.0, r.percentage, 1e-9)
    }

    @Test fun scoreCanGoNegative() {
        val s = session(exam(1.0, count = 2), mapOf("q1" to 0, "q2" to 0))
        val r = scoreSession(s, now = 5_000L)
        assertEquals(-2.0, r.obtainedMarks, 1e-9)
        assertEquals(-100.0, r.percentage, 1e-9)
    }

    @Test fun emptyExamDoesNotDivideByZero() {
        val e = exam(0.0, count = 0)
        val r = scoreSession(session(e, emptyMap()), now = 5_000L)
        assertEquals(0.0, r.percentage, 1e-9)
        assertEquals(0, r.totalQuestions)
    }

    @Test fun timeUsedIsClampedToTheTimeLimit() {
        val e = exam(0.0) // 10 minutes
        val late = scoreSession(session(e, emptyMap(), startedAt = 0L), now = 3_600_000L)
        assertEquals(600_000L, late.timeUsed)
        assertEquals(0L, late.timeRemaining)
        val early = scoreSession(session(e, emptyMap(), startedAt = 0L), now = 120_000L)
        assertEquals(120_000L, early.timeUsed)
        assertEquals(480_000L, early.timeRemaining)
    }

    @Test fun resultCarriesTheIds() {
        val r = scoreSession(session(exam(0.0), emptyMap()), now = 9_999L)
        assertEquals("e1", r.examId)
        assertEquals("s1", r.sessionId)
        assertEquals(9_999L, r.submittedAt)
    }
}
