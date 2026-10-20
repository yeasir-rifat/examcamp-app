package com.examcamp.app.data

import com.examcamp.app.Config
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** The signed-in member, as far as Live Exam is concerned. Email is lowercase. */
data class LiveUser(val email: String, val name: String?)

/** A finished live attempt that has not reached Supabase yet. Kept on the device until it does. */
@Serializable
data class PendingResult(
    val examId: String,
    val email: String,
    val name: String,
    val answers: Map<String, Int>,
    val obtained: Double,
    val total: Double,
)

/** Everything the Live Exam page shows, saved on the phone so it still opens without a network. */
@Serializable
data class LiveSnapshot(
    val subjects: List<LiveSubject>,
    val exams: List<LiveExam>,
    val members: List<LiveMember>,
    val submissions: List<LiveSubmission>,
)

/**
 * Live Exam state and rules, ported from app.js: access (isLiveExamAdmin / memberHasSubjectAccess),
 * the central merit list (refreshMeritFromSupabase), joining a live exam, and reporting a result.
 * Differences from the website are fixed here: pending results are retried, and the enrollment draft is kept.
 */
class LiveExamEngine(
    private val scope: CoroutineScope,
    private val store: LocalStore,
    private val currentUser: () -> LiveUser?,
    private val idToken: suspend () -> String?,
    private val isOnline: () -> Boolean = { true },
    private val cacheDir: File? = null,
) {
    private val _subjects = MutableStateFlow<List<LiveSubject>>(emptyList())
    val subjects: StateFlow<List<LiveSubject>> = _subjects

    private val _exams = MutableStateFlow<List<LiveExam>>(emptyList())
    val exams: StateFlow<List<LiveExam>> = _exams

    private val _members = MutableStateFlow<List<LiveMember>>(emptyList())
    val members: StateFlow<List<LiveMember>> = _members

    private val _submittedIds = MutableStateFlow<Set<String>>(emptySet())
    /** Live exam ids the signed-in member has already submitted. Blocks a second attempt. */
    val submittedIds: StateFlow<Set<String>> = _submittedIds

    private val _merit = MutableStateFlow<List<MeritRow>>(emptyList())
    val merit: StateFlow<List<MeritRow>> = _merit

    private val _summary = MutableStateFlow(LiveSummary())
    private val _myScores = MutableStateFlow<Map<String, Double>>(emptyMap())
    /** This member's own score per live exam id (liveExamOwnScores on the website). Used by History and Statistics. */
    val myScores: StateFlow<Map<String, Double>> = _myScores

    private val _allSubmissions = MutableStateFlow<List<LiveSubmission>>(emptyList())
    /** Every submission (all members, all exams). Admin Results reads it. */
    val allSubmissions: StateFlow<List<LiveSubmission>> = _allSubmissions

    val summary: StateFlow<LiveSummary> = _summary

    private var submissions: List<LiveSubmission> = emptyList()
    private var debounced: Job? = null
    private val pendingLock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val realtime = LiveRealtime(scope) { requestRefresh() }

    private val _loaded = MutableStateFlow(false)
    /** True once the schedule is on screen, from the network or from the copy saved on the phone. Drives the loading skeleton. */
    val loaded: StateFlow<Boolean> = _loaded

    private val _fromCache = MutableStateFlow(false)
    /** True while the page shows the saved copy because the latest reload could not reach the server. */
    val fromCache: StateFlow<Boolean> = _fromCache

    private var freshLoaded = false
    private var lastSaved: LiveSnapshot? = null
    private val cacheMutex = Mutex()

    init {
        scope.launch { restoreCache() }
    }

    private suspend fun restoreCache() {
        val dir = cacheDir ?: return
        val snap = withContext(Dispatchers.IO) {
            try {
                val f = File(dir, CACHE_FILE)
                if (f.exists()) json.decodeFromString<LiveSnapshot>(f.readText()) else null
            } catch (e: Exception) {
                null
            }
        }
        // A network load that finished first is newer than the saved copy: never overwrite it.
        if (snap != null && !freshLoaded) {
            _subjects.value = snap.subjects
            _exams.value = snap.exams
            _members.value = snap.members
            submissions = snap.submissions
            _allSubmissions.value = snap.submissions
            lastSaved = snap
            recompute()
            _fromCache.value = true
            _loaded.value = true
        }
    }

    private suspend fun saveCache(snap: LiveSnapshot) {
        val dir = cacheDir ?: return
        if (snap == lastSaved) return
        lastSaved = snap
        withContext(Dispatchers.IO) {
            cacheMutex.withLock {
                try {
                    dir.mkdirs()
                    val tmp = File(dir, "$CACHE_FILE.tmp")
                    tmp.writeText(json.encodeToString(LiveSnapshot.serializer(), snap))
                    val target = File(dir, CACHE_FILE)
                    if (!tmp.renameTo(target)) {
                        target.writeText(tmp.readText())
                        tmp.delete()
                    }
                } catch (e: Exception) {
                    // Cache only: a full disk must never break the page.
                }
            }
        }
    }

    /** Sign-in or sign-out: the saved copy is shared, but rank, access and "submitted" depend on who is signed in. */
    fun recomputeForUser() = recompute()

    // ---------- loading ----------

    /** Reloads everything now, and sends any results still waiting on the device. */
    fun refresh() {
        scope.launch { loadAll() }
    }

    /** Coalesces bursts of realtime events into one reload. */
    fun requestRefresh() {
        debounced?.cancel()
        debounced = scope.launch {
            delay(400)
            loadAll()
        }
    }

    fun startRealtime() = realtime.start()
    fun stopRealtime() = realtime.stop()

    private suspend fun loadAll() {
        try {
            val subjects = LiveExamApi.fetchSubjects()
            val exams = LiveExamApi.fetchExams()
            val members = LiveExamApi.fetchMembers()
            val subs = LiveExamApi.fetchSubmissions()
            _subjects.value = subjects
            _exams.value = exams
            _members.value = members
            submissions = subs
            _allSubmissions.value = subs
            recompute()
            freshLoaded = true
            _fromCache.value = false
            saveCache(LiveSnapshot(subjects, exams, members, subs))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep the last good data, same as the website. The page says it is showing a saved copy.
            _fromCache.value = true
        }
        _loaded.value = true
        flushPending()
    }

    /**
     * Merit is per course and per live exam: each exam has its own list, ranked by that exam's score.
     * Only exams the admin has published count, so nobody sees a result early.
     * The summary rank is the member's rank in the latest published exam they took.
     */
    private fun recompute() {
        val me = currentUser()?.email
        val published = _exams.value.filter { it.published }
        val publishedIds = published.map { it.id }.toSet()

        val rows = submissions.filter { it.examId in publishedIds }.map { s ->
            MeritRow(
                examId = s.examId,
                email = s.email,
                name = s.name,
                score = round1(s.scorePercent),
                self = me != null && s.email.equals(me, ignoreCase = true),
            )
        }.sortedByDescending { it.score }
        _merit.value = rows

        val mine = rows.filter { it.self }
        val startById = published.associate { it.id to it.startMs }
        val latest = mine.maxByOrNull { startById[it.examId] ?: 0L }
        _summary.value = LiveSummary(
            rank = if (latest != null) rows.filter { it.examId == latest.examId }.indexOfFirst { it.self } + 1 else 0,
            given = mine.size,
            avgPercent = if (mine.isNotEmpty()) round1(mine.map { it.score }.average()) else 0.0,
        )
        _submittedIds.value = if (me == null) emptySet()
        else submissions.filter { it.email == me }.map { it.examId }.toSet()
        _myScores.value = if (me == null) emptyMap()
        else submissions.filter { it.email == me }.associate { it.examId to it.scorePercent }
    }

    private fun round1(value: Double): Double = Math.round(value * 10) / 10.0

    // ---------- access ----------

    /** Admins (hardcoded list or admin/assistant role) can see and join every subject. */
    fun isLiveAdmin(email: String?): Boolean {
        if (email == null) return false
        if (email in Config.LIVE_EXAM_ADMIN_EMAILS) return true
        return _members.value.any { it.email == email && (it.role == "admin" || it.role == "assistant") }
    }

    /** Whether the signed-in member may join live exams of this subject. */
    fun hasSubjectAccess(subject: String): Boolean {
        val user = currentUser() ?: return false
        if (isLiveAdmin(user.email)) return true
        return _members.value.any { it.email == user.email && subject in it.subjects }
    }

    fun memberName(email: String): String =
        _members.value.firstOrNull { it.email == email }?.name?.takeIf { it.isNotBlank() } ?: email

    // ---------- joining ----------

    /** Checks every join rule in the same order as the website, then fetches the questions. */
    suspend fun prepareJoin(exam: LiveExam): JoinResult {
        if (currentUser() == null) return JoinResult.NotEnrolled
        if (!hasSubjectAccess(exam.subject)) return JoinResult.NotEnrolled
        if (!exam.isLiveAt(System.currentTimeMillis())) return JoinResult.NotLive
        if (exam.id in _submittedIds.value) return JoinResult.AlreadySubmitted
        val token = runCatching { idToken() }.getOrNull()
            ?: return JoinResult.Failed("Sign in again to join this exam.")
        val questions = try {
            LiveExamApi.fetchQuestions(exam.id, token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return JoinResult.Failed(e.message ?: "Could not load questions.")
        }
        if (questions.isEmpty()) return JoinResult.NoQuestions
        return JoinResult.Ready(exam, questions)
    }

    /** Answer keys for a Live Exam attempt restored after an app restart (they are not kept on disk). */
    suspend fun questionsForRestore(examId: String): List<Question>? {
        val token = runCatching { idToken() }.getOrNull() ?: return null
        return runCatching { LiveExamApi.fetchQuestions(examId, token) }.getOrNull()
    }

    // ---------- results (with retry) ----------

    /**
     * Saves a finished live attempt on the device first, then uploads it. If the upload fails
     * (offline, server error) it stays queued and is retried on the next reload or app start.
     */
    fun reportResult(session: Session) {
        val examId = session.exam.liveExamId
        val result = session.result ?: return
        if (examId.isEmpty()) return
        val user = currentUser() ?: return
        val pending = PendingResult(
            examId = examId,
            email = user.email,
            name = memberName(user.email),
            answers = session.answers,
            obtained = result.obtainedMarks,
            total = result.totalMarks,
        )
        scope.launch {
            pendingLock.withLock {
                writePending(readPending() + pending)
            }
            flushPending()
        }
    }

    /** Uploads queued results. Successful ones are removed; the rest wait for the next attempt. */
    private suspend fun flushPending() {
        var uploaded = 0
        pendingLock.withLock {
            val queue = readPending()
            if (queue.isEmpty()) return
            val remaining = mutableListOf<PendingResult>()
            for (p in queue) {
                val ok = try {
                    LiveExamApi.upsertSubmission(p.examId, p.email, p.name, p.answers, p.obtained, p.total)
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
                if (ok) uploaded++ else remaining.add(p)
            }
            writePending(remaining)
        }
        if (uploaded > 0) refresh()
    }

    private suspend fun readPending(): List<PendingResult> {
        val raw = store.blobOnce(LocalStore.GUEST, PENDING_KEY) ?: return emptyList()
        return runCatching { json.decodeFromString<List<PendingResult>>(raw) }.getOrDefault(emptyList())
    }

    private suspend fun writePending(list: List<PendingResult>) {
        store.setBlob(
            LocalStore.GUEST,
            PENDING_KEY,
            if (list.isEmpty()) null else json.encodeToString(ListSerializer(PendingResult.serializer()), list),
        )
    }

    /** Public "Enroll Now" form. Returns false when the insert fails, so the form can stay open. */
    suspend fun submitEnrollment(draft: EnrollmentDraft): Boolean =
        try {
            LiveExamApi.insertEnrollment(draft)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    // ---------- enrollment draft (kept on the device) ----------

    suspend fun loadEnrollDraft(): EnrollFormState? {
        val raw = store.blobOnce(LocalStore.GUEST, DRAFT_KEY) ?: return null
        return runCatching { json.decodeFromString<EnrollFormState>(raw) }.getOrNull()
    }

    /** Pass null to clear the draft (after a successful send). */
    fun saveEnrollDraft(state: EnrollFormState?) {
        scope.launch {
            store.setBlob(LocalStore.GUEST, DRAFT_KEY, state?.let { json.encodeToString(EnrollFormState.serializer(), it) })
        }
    }

    private companion object {
        const val PENDING_KEY = "live_pending_results"
        const val CACHE_FILE = "live-cache.json"
        const val DRAFT_KEY = "live_enroll_draft"
    }
}
