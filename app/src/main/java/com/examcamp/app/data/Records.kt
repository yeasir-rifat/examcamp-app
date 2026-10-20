package com.examcamp.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.doubleOrNull

/**
 * History and Statistics records (renderHistoryPage, renderStatisticsPage and getLiveExamResultRecords
 * in app.js). Practice rows are read from exam_history as raw JSON, so website and app entries both work.
 */

/** One row of the Practice Exams tab: an entry of exam_history. */
data class PracticeRow(
    val sessionId: String,
    val subject: String,
    val topic: String,
    val questionCount: Int,
    val evaluate: Boolean,
    val completed: Boolean,
    val percentage: Double?,
    val answered: Int,
    val dateMs: Long,
    val raw: JsonObject,
)

/** One row of the Live Exam tab: a published, ended live exam with this member's own score on it. */
data class LiveRecord(val exam: LiveExam, val percentage: Double?)

/** The three numbers of a Statistics overview card. */
data class Overview(val count: Int, val avg: Int, val questions: Int)

private val recordJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

fun practiceRows(history: List<JsonElement>): List<PracticeRow> = history.mapNotNull { el ->
    val o = el as? JsonObject ?: return@mapNotNull null
    val exam = o["exam"] as? JsonObject
    val result = o["result"] as? JsonObject
    val dateMs = listOf(result?.num("submittedAt"), o.num("updatedAt"), o.num("startedAt"))
        .firstOrNull { it != null && it > 0 }?.toLong() ?: 0L
    PracticeRow(
        sessionId = o.str("sessionId").orEmpty(),
        subject = exam?.str("subject").orEmpty(),
        topic = exam?.str("topic").orEmpty(),
        questionCount = (exam?.get("questions") as? JsonArray)?.size ?: 0,
        evaluate = !exam?.str("practiceEvalId").isNullOrEmpty(),
        completed = o.str("status") == "completed",
        percentage = result?.num("percentage"),
        answered = (o["answers"] as? JsonObject)?.size ?: 0,
        dateMs = dateMs,
        raw = o,
    )
}

/** Published live exams that have ended, newest first. The score is this member's own score on that exam. */
fun liveRecords(exams: List<LiveExam>, ownScores: Map<String, Double>, now: Long): List<LiveRecord> =
    exams.filter { it.published && now >= it.endMs }
        .sortedByDescending { it.endMs }
        .map { LiveRecord(it, ownScores[it.id]) }

/** Turns a stored exam_history entry back into a Session so the results screen can show it. */
fun decodeSessionRecord(raw: JsonObject): Session? =
    runCatching { recordJson.decodeFromJsonElement(Session.serializer(), raw) }.getOrNull()

fun practiceOverview(rows: List<PracticeRow>): Overview {
    val done = rows.filter { it.completed }
    val scores = done.mapNotNull { it.percentage }
    return Overview(
        count = done.size,
        avg = if (scores.isEmpty()) 0 else Math.round(scores.average()).toInt(),
        questions = done.sumOf { it.questionCount },
    )
}

fun liveOverview(records: List<LiveRecord>): Overview {
    val scores = records.mapNotNull { it.percentage }
    return Overview(
        count = records.size,
        avg = if (scores.isEmpty()) 0 else Math.round(scores.average()).toInt(),
        questions = records.sumOf { it.exam.questionCount },
    )
}

// ---------- tombstones (deleted history entries) ----------
//
// exam_history is merged by sessionId, newest updatedAt wins. A plain delete would let another device
// that still holds the record bring it back at the next sync. So a deleted entry is replaced by a
// small marker {sessionId, deleted: true, updatedAt}. It is newer than the record it replaces, so it
// wins every merge and the deletion spreads to all devices (the website understands it too).

const val TOMBSTONE_TTL_MS = 30L * 24 * 60 * 60 * 1000
const val TOMBSTONE_MAX = 200

fun isTombstone(el: JsonElement): Boolean =
    ((el as? JsonObject)?.get("deleted") as? JsonPrimitive)?.booleanOrNull == true

fun makeTombstone(sessionId: String, now: Long): JsonObject = buildJsonObject {
    put("sessionId", sessionId)
    put("deleted", true)
    put("updatedAt", now)
}

private fun updatedAtOf(el: JsonElement): Double = (el as? JsonObject)?.num("updatedAt") ?: 0.0

/**
 * Port of mergeExamHistories() from the website: one entry per sessionId, the newest updatedAt wins
 * (a tombstone is just another entry, so a newer tombstone beats an older record), newest first.
 */
fun mergeHistories(a: List<JsonElement>, b: List<JsonElement>): List<JsonElement> {
    val bySession = LinkedHashMap<String, JsonElement>()
    (a + b).forEach { rec ->
        val id = (rec as? JsonObject)?.str("sessionId") ?: return@forEach
        val existing = bySession[id]
        if (existing == null || updatedAtOf(rec) > updatedAtOf(existing)) bySession[id] = rec
    }
    return bySession.values.sortedByDescending(::updatedAtOf)
}

/** What the screens may show: everything except deletion markers. */
fun visibleHistory(list: List<JsonElement>): List<JsonElement> = list.filterNot(::isTombstone)

/**
 * The stored list: newest [max] real entries, plus recent tombstones (they do not use up the [max]
 * slots, and are dropped after 30 days so the list cannot grow forever).
 */
fun capHistory(list: List<JsonElement>, max: Int, now: Long): List<JsonElement> {
    val live = list.filterNot(::isTombstone).sortedByDescending(::updatedAtOf).take(max)
    val marks = list.filter(::isTombstone)
        .filter { now - updatedAtOf(it) <= TOMBSTONE_TTL_MS }
        .sortedByDescending(::updatedAtOf)
        .take(TOMBSTONE_MAX)
    return (live + marks).sortedByDescending(::updatedAtOf)
}
