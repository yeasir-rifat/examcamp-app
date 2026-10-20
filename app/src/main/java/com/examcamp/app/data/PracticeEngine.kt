package com.examcamp.app.data

import android.content.Context
import com.examcamp.app.SyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Practice system, ported from app.js (practiceState, refreshPracticeManifest, enterPracticeMode,
 * savePracticeProgress, practice_progress sync):
 *  - the category/subject/topic tree comes from the Edge Function and is cached on disk,
 *  - question banks are fetched per topic and cached on disk for offline use,
 *  - progress is saved locally first (per account) and mirrored to public.practice_progress.
 */
class PracticeEngine(
    context: Context,
    private val scope: CoroutineScope,
    private val namespace: StateFlow<String>,
    private val currentUid: () -> String?,
    private val onSync: (SyncStatus) -> Unit,
    private val isOnline: () -> Boolean = { true },
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val dir = File(context.filesDir, "practice").apply { mkdirs() }
    private val fileMutex = Mutex()
    private val progressSerializer = MapSerializer(String.serializer(), PracticeEntry.serializer())
    private val categoriesSerializer = ListSerializer(PracticeCategory.serializer())
    private val questionsSerializer = ListSerializer(PracticeQuestion.serializer())

    private val _categories = MutableStateFlow(PracticeDefaults.categories())
    val categories: StateFlow<List<PracticeCategory>> = _categories

    private val _progress = MutableStateFlow<Map<String, PracticeEntry>>(emptyMap())
    val progress: StateFlow<Map<String, PracticeEntry>> = _progress

    private val _run = MutableStateFlow<PracticeRun?>(null)
    val run: StateFlow<PracticeRun?> = _run

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    /** Progress of "download everything for offline". [saved] / [total] topics of the chosen category. */
    data class DownloadState(
        val categoryId: String = "",
        val running: Boolean = false,
        val total: Int = 0,
        val saved: Int = 0,
        val failed: Int = 0,
        /** Set once a run ends without being cancelled: the screen shows a result line. */
        val finished: Boolean = false,
    )

    private val _download = MutableStateFlow(DownloadState())
    val download: StateFlow<DownloadState> = _download
    private var downloadJob: Job? = null

    private val memCache = HashMap<String, List<PracticeQuestion>>()
    private var persistJob: Job? = null
    private var pending = LinkedHashMap<String, PracticeEntry>()
    private var cloudJob: Job? = null

    init {
        scope.launch {
            readFile("manifest.json")?.let { raw ->
                try {
                    json.decodeFromString(categoriesSerializer, raw).takeIf { it.isNotEmpty() }?.let { _categories.value = it }
                } catch (e: Exception) { /* keep defaults */ }
            }
            refreshManifest()
        }
        scope.launch {
            namespace.collect { ns -> _progress.value = loadProgress(ns) }
        }
    }

    // ---------- files ----------

    private fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9_-]"), "_")
    private fun progressFile(ns: String) = "progress-${safe(ns)}.json"
    private fun questionsFile(key: String) = "q_${safe(key)}_${key.hashCode().toUInt().toString(16)}.json"

    private suspend fun readFile(name: String): String? = withContext(Dispatchers.IO) {
        try { File(dir, name).takeIf { it.exists() }?.readText() } catch (e: Exception) { null }
    }

    private suspend fun writeFile(name: String, text: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            try { File(dir, name).writeText(text) } catch (e: Exception) { /* disk full etc.: cache only */ }
        }
    }

    private suspend fun deleteFile(name: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock { try { File(dir, name).delete() } catch (e: Exception) { } }
    }

    private suspend fun loadProgress(ns: String): Map<String, PracticeEntry> =
        readFile(progressFile(ns))?.let {
            try { json.decodeFromString(progressSerializer, it) } catch (e: Exception) { null }
        } ?: emptyMap()

    private suspend fun writeProgress(ns: String, map: Map<String, PracticeEntry>) =
        writeFile(progressFile(ns), json.encodeToString(progressSerializer, map))

    // ---------- manifest (category → subject → topic tree) ----------

    /** Fetches the latest tree; on any failure the cached/default tree stays exactly as it was. */
    suspend fun refreshManifest(): Boolean {
        if (_refreshing.value) return false
        _refreshing.value = true
        return try {
            val remote = PracticeApi.fetchManifest()
            if (remote.isEmpty()) false else { applyManifest(remote); true }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        } finally {
            _refreshing.value = false
        }
    }

    /**
     * Merges the server tree in. Progress saved under an id that changed server-side (same
     * category/subject/topic names, new ids) is remapped and re-queued so it is never orphaned.
     */
    private suspend fun applyManifest(remote: List<PracticeCategory>) {
        val oldLookup = HashMap<String, Pair<String, String>>() // "cat::subject::topic" -> (oldSubjectId, oldTopicId)
        _categories.value.forEach { cat ->
            cat.subjects.forEach { s -> s.topics.forEach { tp -> oldLookup["${cat.id}::${s.name}::${tp.name}"] = s.id to tp.id } }
        }
        val remapped = LinkedHashMap<String, String>() // newKey -> oldKey
        remote.forEach { cat ->
            cat.subjects.forEach { s ->
                s.topics.forEach { tp ->
                    val prior = oldLookup["${cat.id}::${s.name}::${tp.name}"]
                    if (prior != null && (prior.first != s.id || prior.second != tp.id)) {
                        remapped[practiceKey(s.id, tp.id)] = practiceKey(prior.first, prior.second)
                    }
                }
            }
        }
        if (remapped.isNotEmpty()) {
            val prog = _progress.value.toMutableMap()
            val queued = mutableListOf<Pair<String, PracticeEntry>>()
            remapped.forEach { (newKey, oldKey) ->
                val entry = prog[oldKey]
                if (entry != null && prog[newKey] == null) {
                    prog[newKey] = entry
                    queued += newKey to entry
                }
            }
            if (queued.isNotEmpty()) {
                _progress.value = prog
                persistProgress()
                queued.forEach { (k, e) -> queueCloud(k, e) }
            }
        }
        _categories.value = remote
        writeFile("manifest.json", json.encodeToString(categoriesSerializer, remote))
    }

    fun findTopic(subjectId: String, topicId: String): Pair<PracticeSubject, PracticeTopic>? {
        _categories.value.forEach { cat ->
            val subject = cat.subjects.find { it.id == subjectId } ?: return@forEach
            val topic = subject.topics.find { it.id == topicId } ?: return@forEach
            return subject to topic
        }
        return null
    }

    // ---------- question banks ----------

    /** Network first (always the current bank); falls back to the last saved copy when offline. */
    suspend fun loadQuestions(subjectId: String, topicId: String, force: Boolean = false): List<PracticeQuestion> {
        val key = practiceKey(subjectId, topicId)
        if (!force) memCache[key]?.let { return it }
        // No network: go straight to the copy saved on the phone instead of waiting for a timeout.
        if (!isOnline()) {
            cachedQuestions(key)?.let { memCache[key] = it; return it }
        }
        try {
            val fresh = PracticeApi.fetchTopicQuestions(subjectId, topicId)
            memCache[key] = fresh
            writeFile(questionsFile(key), json.encodeToString(questionsSerializer, fresh))
            return fresh
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val cached = readFile(questionsFile(key))?.let {
                try { json.decodeFromString(questionsSerializer, it) } catch (x: Exception) { null }
            }
            if (cached != null) {
                memCache[key] = cached
                return cached
            }
            throw e
        }
    }

    // ---------- download everything for offline ----------

    private fun topicsOf(category: PracticeCategory): List<Pair<PracticeSubject, PracticeTopic>> =
        category.subjects.flatMap { s -> s.topics.map { s to it } }

    /** How many of this category's topics already have a saved question bank on the phone. */
    suspend fun savedTopicCount(category: PracticeCategory): Int = withContext(Dispatchers.IO) {
        topicsOf(category).count { (s, t) -> File(dir, questionsFile(practiceKey(s.id, t.id))).exists() }
    }

    /**
     * Fetches the question bank of every topic in [category] and saves it on the phone. Banks are
     * written straight to disk and not kept in memory, so thousands of questions cannot fill RAM.
     * A topic that fails keeps its previously saved copy. Three topics download at a time.
     */
    fun canDownload(): Boolean = isOnline()

    fun downloadAll(category: PracticeCategory) {
        if (_download.value.running) return
        val topics = topicsOf(category)
        if (topics.isEmpty()) return
        _download.value = DownloadState(categoryId = category.id, running = true, total = topics.size)
        downloadJob = scope.launch {
            val gate = Semaphore(3)
            var saved = 0
            var failed = 0
            try {
                coroutineScope {
                    topics.map { (s, t) ->
                        async {
                            gate.acquire()
                            try {
                                val ok = try {
                                    val fresh = PracticeApi.fetchTopicQuestions(s.id, t.id)
                                    val key = practiceKey(s.id, t.id)
                                    memCache.remove(key) // next open reads the new copy
                                    writeFile(questionsFile(key), json.encodeToString(questionsSerializer, fresh))
                                    true
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    false
                                }
                                if (ok) saved++ else failed++
                                _download.value = _download.value.copy(saved = saved, failed = failed)
                            } finally {
                                gate.release()
                            }
                        }
                    }.awaitAll()
                }
                _download.value = _download.value.copy(running = false, finished = true)
            } catch (e: CancellationException) {
                _download.value = DownloadState() // cancelled by the user: back to the idle button
                throw e
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    /** Hides the result line after the user has seen it. */
    fun clearDownloadResult() {
        if (!_download.value.running) _download.value = DownloadState()
    }

    private suspend fun cachedQuestions(key: String): List<PracticeQuestion>? =
        readFile(questionsFile(key))?.let {
            try { json.decodeFromString(questionsSerializer, it) } catch (x: Exception) { null }
        }

    /** Subtopic names with their question counts, in bank order (openPracticeTopicEntry). */
    fun subtopicCounts(questions: List<PracticeQuestion>): List<Pair<String, Int>> {
        val counts = LinkedHashMap<String, Int>()
        questions.forEach { q ->
            val name = q.subtopic.trim()
            if (name.isNotEmpty()) counts[name] = (counts[name] ?: 0) + 1
        }
        return counts.map { it.key to it.value }
    }

    // ---------- Practice Mode ----------

    suspend fun openRun(subjectId: String, topicId: String, subtopic: String?): PracticeOpenError? {
        val (subject, topic) = findTopic(subjectId, topicId) ?: return PracticeOpenError.NOT_FOUND
        var questions = try {
            loadQuestions(subjectId, topicId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return PracticeOpenError.LOAD_FAILED
        }
        if (subtopic != null) questions = questions.filter { it.subtopic.trim() == subtopic }
        if (questions.isEmpty()) return PracticeOpenError.EMPTY
        // A subtopic run never reads or overwrites the whole-topic saved progress.
        val saved = if (subtopic == null) _progress.value[practiceKey(subjectId, topicId)] else null
        _run.value = PracticeRun(
            subjectId = subjectId,
            topicId = topicId,
            subtopic = subtopic,
            subjectName = subject.name,
            topicName = topic.name,
            language = topic.language,
            questions = questions,
            answers = saved?.answers.orEmpty(),
            current = (saved?.current ?: 0).coerceIn(0, questions.size - 1),
        )
        return null
    }

    /** Picking an option locks it: an answered question cannot be changed (same as the website). */
    fun answer(questionId: String, optionIndex: Int) {
        val r = _run.value ?: return
        if (r.answers[questionId] != null) return
        val next = r.copy(answers = r.answers + (questionId to optionIndex))
        _run.value = next
        saveProgress(next)
    }

    fun setCurrent(index: Int) {
        val r = _run.value ?: return
        val i = index.coerceIn(0, (r.questions.size - 1).coerceAtLeast(0))
        if (i != r.current) _run.value = r.copy(current = i)
    }

    fun closeRun() {
        _run.value?.let { saveProgress(it) }
        _run.value = null
    }

    private fun saveProgress(r: PracticeRun) {
        if (r.subtopic != null) return
        val key = practiceKey(r.subjectId, r.topicId)
        val entry = PracticeEntry(
            answers = r.answers,
            current = r.current,
            answeredCount = r.answers.size,
            updatedAt = System.currentTimeMillis(),
        )
        _progress.value = _progress.value + (key to entry)
        persistProgress()
        queueCloud(key, entry)
    }

    // ---------- Evaluate (modules → Exam Mode) ----------

    /** Shuffle once, cut into fixed-size, non-overlapping modules (buildEvaluateModules). */
    fun evaluateModules(questions: List<PracticeQuestion>): List<List<PracticeQuestion>> =
        questions.shuffled().chunked(EVALUATE_MODULE_SIZE)

    fun buildEvaluateExam(
        subject: PracticeSubject,
        topic: PracticeTopic,
        module: List<PracticeQuestion>,
        moduleNumber: Int,
        negativeMarking: Double,
        examId: String,
    ): Exam {
        val questions = module.mapIndexed { i, q ->
            Question(
                id = "question-${i + 1}",
                question = GeminiApi.autoWrapStrayLatex(q.question),
                options = q.options.map { GeminiApi.autoWrapStrayLatex(it) },
                correctAnswer = q.correctAnswer,
                explanation = GeminiApi.autoWrapStrayLatex(q.explanation),
            )
        }
        return Exam(
            examId = examId,
            subject = subject.name,
            topic = "${topic.name} (Module $moduleNumber)",
            language = topic.language,
            questionCount = questions.size,
            duration = EVALUATE_MODULE_DURATION_MINUTES,
            marksPerQuestion = 1.0,
            negativeMarking = negativeMarking,
            practiceEvalId = "${subject.id}::${topic.id}::module-$moduleNumber",
            questions = questions,
        )
    }

    // ---------- saving + cloud sync ----------

    private fun persistProgress() {
        val ns = namespace.value
        val snapshot = _progress.value
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(250)
            writeProgress(ns, snapshot)
        }
    }

    private fun queueCloud(key: String, entry: PracticeEntry) {
        if (currentUid() == null) return
        pending[key] = entry
        onSync(SyncStatus.SYNCING)
        cloudJob?.cancel()
        cloudJob = scope.launch {
            delay(600)
            flushCloud()
        }
    }

    private suspend fun flushCloud() {
        val uid = currentUid() ?: return
        if (pending.isEmpty()) return
        val batch = pending
        pending = LinkedHashMap()
        val rows = batch.map { (key, entry) ->
            val (subjectId, topicId) = key.split("::").let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
            val found = findTopic(subjectId, topicId)
            SupabaseApi.PracticeProgressRow(subjectId, topicId, found?.first?.name, found?.second?.name, entry)
        }
        try {
            SupabaseApi.upsertPracticeProgress(uid, rows)
            onSync(SyncStatus.SYNCED)
        } catch (e: CancellationException) {
            batch.forEach { (k, v) -> pending.putIfAbsent(k, v) }
            throw e
        } catch (e: Exception) {
            batch.forEach { (k, v) -> pending.putIfAbsent(k, v) }
            onSync(SyncStatus.ERROR)
        }
    }

    /** The connection is back: send any progress that could not be uploaded earlier. */
    fun retryPending() {
        scope.launch { flushCloud() }
    }

    /** App going to the background: write the open run and anything still waiting for the cloud. */
    fun flush() {
        _run.value?.let { saveProgress(it) }
        val ns = namespace.value
        val snapshot = _progress.value
        persistJob?.cancel()
        cloudJob?.cancel()
        scope.launch {
            withContext(NonCancellable) {
                writeProgress(ns, snapshot)
                flushCloud()
            }
        }
    }

    /**
     * Sign-in (pullPracticeProgress): keep whichever copy of each topic is newer, fold in progress made
     * while signed out on a fresh sign-in, and push anything the server doesn't have yet.
     */
    suspend fun onSignedIn(uid: String, fresh: Boolean) {
        val cloud = SupabaseApi.fetchPracticeProgress(uid)
        var local = loadProgress(uid)
        if (fresh) {
            val guest = loadProgress(LocalStore.GUEST)
            if (guest.isNotEmpty()) {
                local = mergeNewest(local, guest)
                deleteFile(progressFile(LocalStore.GUEST))
            }
        }
        val merged = local.toMutableMap()
        cloud.forEach { row ->
            val key = practiceKey(row.subjectId, row.topicId)
            val mine = merged[key]
            if (mine == null || row.entry.updatedAt >= mine.updatedAt) merged[key] = row.entry
        }
        val cloudByKey = cloud.associateBy { practiceKey(it.subjectId, it.topicId) }
        merged.forEach { (key, entry) ->
            val row = cloudByKey[key]
            if (row == null || entry.updatedAt > row.entry.updatedAt) queueCloud(key, entry)
        }
        writeProgress(uid, merged)
        if (namespace.value == uid) _progress.value = merged
    }

    private fun mergeNewest(a: Map<String, PracticeEntry>, b: Map<String, PracticeEntry>): Map<String, PracticeEntry> {
        val out = a.toMutableMap()
        b.forEach { (k, v) -> if (out[k]?.let { v.updatedAt > it.updatedAt } != false) out[k] = v }
        return out
    }

    companion object {
        const val EVALUATE_MODULE_SIZE = 50
        const val EVALUATE_MODULE_DURATION_MINUTES = 30
    }
}
