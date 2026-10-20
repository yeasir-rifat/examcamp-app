package com.examcamp.app.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {

    private fun rec(id: String, updatedAt: Long, note: String = ""): JsonObject = buildJsonObject {
        put("sessionId", id)
        put("updatedAt", updatedAt)
        if (note.isNotEmpty()) put("note", note)
    }

    private fun ids(list: List<JsonElement>) = list.map { ((it as JsonObject)["sessionId"] as JsonPrimitive).content }

    private val day = 24L * 60 * 60 * 1000

    // ---------- mergeHistories ----------

    @Test fun mergeKeepsTheNewerCopyOfASession() {
        val merged = mergeHistories(listOf(rec("a", 100, "old")), listOf(rec("a", 200, "new")))
        assertEquals(1, merged.size)
        assertEquals("new", ((merged[0] as JsonObject)["note"] as JsonPrimitive).content)
    }

    @Test fun mergeKeepsTheLocalCopyWhenItIsNewer() {
        val merged = mergeHistories(listOf(rec("a", 300, "local")), listOf(rec("a", 200, "cloud")))
        assertEquals("local", ((merged[0] as JsonObject)["note"] as JsonPrimitive).content)
    }

    @Test fun mergeUnionsDifferentSessionsNewestFirst() {
        val merged = mergeHistories(listOf(rec("a", 100), rec("c", 300)), listOf(rec("b", 200)))
        assertEquals(listOf("c", "b", "a"), ids(merged))
    }

    @Test fun mergeIgnoresEntriesWithoutASessionId() {
        val merged = mergeHistories(listOf(buildJsonObject { put("updatedAt", 5) }, JsonPrimitive("junk")), listOf(rec("a", 1)))
        assertEquals(listOf("a"), ids(merged))
    }

    @Test fun mergeIsTheSameInBothDirections() {
        val a = listOf(rec("x", 1), rec("y", 5))
        val b = listOf(rec("x", 3), rec("z", 4))
        assertEquals(ids(mergeHistories(a, b)), ids(mergeHistories(b, a)))
    }

    // ---------- tombstones ----------

    @Test fun aNewerTombstoneBeatsTheRecordItDeletes() {
        val merged = mergeHistories(listOf(rec("a", 100)), listOf(makeTombstone("a", now = 200)))
        assertEquals(1, merged.size)
        assertTrue(isTombstone(merged[0]))
        assertTrue(visibleHistory(merged).isEmpty())
    }

    @Test fun aDeletedExamDoesNotComeBackFromAnotherDevice() {
        val thisPhone = listOf(makeTombstone("a", now = 500))
        val otherDeviceStillHasIt = listOf(rec("a", 400))
        assertTrue(visibleHistory(mergeHistories(thisPhone, otherDeviceStillHasIt)).isEmpty())
    }

    @Test fun anExamRestartedAfterDeletionWins() {
        val merged = mergeHistories(listOf(makeTombstone("a", now = 500)), listOf(rec("a", 900)))
        assertFalse(isTombstone(merged[0]))
    }

    @Test fun tombstoneShape() {
        val t = makeTombstone("a", now = 42)
        assertEquals("a", (t["sessionId"] as JsonPrimitive).content)
        assertEquals(true, (t["deleted"] as JsonPrimitive).booleanOrNull)
        assertEquals(42L, (t["updatedAt"] as JsonPrimitive).content.toLong())
    }

    // ---------- capHistory ----------

    @Test fun capKeepsTheNewestRealEntries() {
        val list = (1..60).map { rec("s$it", it.toLong()) }
        val capped = capHistory(list, max = 50, now = 1_000L)
        assertEquals(50, capped.size)
        assertEquals("s60", ids(capped).first())
        assertEquals("s11", ids(capped).last())
    }

    @Test fun tombstonesDoNotUseUpTheRealSlots() {
        val now = 100 * day
        val list = (1..50).map { rec("s$it", now - it) } + (1..10).map { makeTombstone("d$it", now - it) }
        val capped = capHistory(list, max = 50, now = now)
        assertEquals(50, visibleHistory(capped).size)
        assertEquals(10, capped.count(::isTombstone))
    }

    @Test fun oldTombstonesExpireAfterThirtyDays() {
        val now = 100 * day
        val list = listOf(makeTombstone("fresh", now - 29 * day), makeTombstone("stale", now - 31 * day))
        assertEquals(listOf("fresh"), ids(capHistory(list, max = 50, now = now)))
    }

    @Test fun tombstoneCountIsCapped() {
        val now = 100 * day
        val list = (1..250).map { makeTombstone("d$it", now - it) }
        assertEquals(TOMBSTONE_MAX, capHistory(list, max = 50, now = now).size)
    }

    // ---------- overview numbers ----------

    private fun row(done: Boolean, pct: Double?, q: Int) = PracticeRow(
        sessionId = "s", subject = "", topic = "", questionCount = q, evaluate = false,
        completed = done, percentage = pct, answered = 0, dateMs = 0, raw = buildJsonObject { },
    )

    @Test fun practiceOverviewCountsOnlyCompletedExams() {
        val o = practiceOverview(listOf(row(true, 80.0, 10), row(true, 61.0, 20), row(false, null, 30)))
        assertEquals(2, o.count)
        assertEquals(71, o.avg) // (80 + 61) / 2 = 70.5, rounds to 71
        assertEquals(30, o.questions)
    }

    @Test fun practiceOverviewOfNothingIsZero() {
        val o = practiceOverview(emptyList())
        assertEquals(Overview(0, 0, 0), o)
    }
}
