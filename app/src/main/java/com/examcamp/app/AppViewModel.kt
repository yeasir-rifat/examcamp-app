package com.examcamp.app

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.examcamp.app.auth.AuthManager
import com.examcamp.app.auth.AuthState
import com.examcamp.app.data.AiConfig
import com.examcamp.app.data.CloudDoc
import com.examcamp.app.data.ExamEngine
import com.examcamp.app.data.ExamPrefs
import com.examcamp.app.data.AdminEngine
import com.examcamp.app.data.PracticeAdminEngine
import com.examcamp.app.data.Exam
import com.examcamp.app.data.JoinResult
import com.examcamp.app.data.LiveExamEngine
import com.examcamp.app.data.LiveUser
import com.examcamp.app.data.LocalStore
import com.examcamp.app.data.capHistory
import com.examcamp.app.data.isTombstone
import com.examcamp.app.data.makeTombstone
import com.examcamp.app.data.visibleHistory
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import com.examcamp.app.data.PracticeEngine
import com.examcamp.app.data.SupabaseApi
import com.examcamp.app.data.mergeHistories
import com.examcamp.app.i18n.I18n
import com.examcamp.app.i18n.Lang
import com.examcamp.app.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

enum class SyncStatus { SYNCED, SYNCING, ERROR, OFFLINE }

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val ec = app as ExamcampApp
    private val store: LocalStore = ec.store
    private val authManager: AuthManager = ec.auth
    private val json = Json { ignoreUnknownKeys = true }

    val auth: StateFlow<AuthState> = authManager.state

    // Read once up front so the very first frame already uses the saved theme and language.
    private val initialSettings = runBlocking(Dispatchers.IO) { store.settings.first() }
    val settings: StateFlow<LocalStore.Settings> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, initialSettings)

    private val _sync = MutableStateFlow(SyncStatus.SYNCED)
    val sync: StateFlow<SyncStatus> = _sync

    /** Whether the phone reports a network. The UI shows a banner while it is false. */
    val online: StateFlow<Boolean> = ec.network.online

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events

    /** Storage namespace of whoever is using the app right now (Firebase uid or "guest"). */
    private val namespace: StateFlow<String> = authManager.state
        .map { (it as? AuthState.SignedIn)?.user?.uid ?: LocalStore.GUEST }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalStore.GUEST)

    /** Home quick-stat tiles, computed from completed exams in the history (renderHomeQuickStats in app.js). */
    data class QuickStats(val exams: Int = 0, val avgPercent: Int = 0, val questions: Int = 0)

    val quickStats: StateFlow<QuickStats> = namespace
        .flatMapLatest { store.blob(it, LocalStore.EXAM_HISTORY) }
        .map { raw -> computeQuickStats(visibleHistory(parseArray(raw))) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, QuickStats())

    /** Raw exam_history entries (Practice and AI exams). History and Statistics read them. */
    val examHistory: StateFlow<List<JsonElement>> = namespace
        .flatMapLatest { store.blob(it, LocalStore.EXAM_HISTORY) }
        .map { visibleHistory(parseArray(it)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * History swipe-to-delete. The entry is replaced by a tombstone (see Records.kt) and synced like any
     * other history change. Returns false if nothing was found to delete.
     */
    suspend fun deleteHistoryRecord(sessionId: String): Boolean {
        val ns = namespace.value
        val list = parseArray(store.blobOnce(ns, LocalStore.EXAM_HISTORY)).toMutableList()
        val idx = list.indexOfFirst { (it as? JsonObject)?.get("sessionId")?.jsonPrimitive?.contentOrNull == sessionId && !isTombstone(it) }
        if (idx < 0) return false
        val now = System.currentTimeMillis()
        list[idx] = makeTombstone(sessionId, now)
        saveDocNow(LocalStore.EXAM_HISTORY, JsonArray(capHistory(list, ExamEngine.HISTORY_MAX, now)).toString())
        return true
    }

    /** Undo: the original entry comes back with a newer updatedAt, so it beats the tombstone everywhere. */
    suspend fun restoreHistoryRecord(original: JsonObject) {
        val sessionId = original["sessionId"]?.jsonPrimitive?.contentOrNull ?: return
        val ns = namespace.value
        val now = System.currentTimeMillis()
        val revived = JsonObject(original + ("updatedAt" to JsonPrimitive(now)))
        val list = parseArray(store.blobOnce(ns, LocalStore.EXAM_HISTORY))
            .filterNot { (it as? JsonObject)?.get("sessionId")?.jsonPrimitive?.contentOrNull == sessionId } + revived
        saveDocNow(LocalStore.EXAM_HISTORY, JsonArray(capHistory(list, ExamEngine.HISTORY_MAX, now)).toString())
    }

    private fun computeQuickStats(history: List<JsonElement>): QuickStats {
        var exams = 0
        var pctSum = 0.0
        var questions = 0
        history.forEach { rec ->
            val o = rec as? JsonObject ?: return@forEach
            if (o["status"]?.jsonPrimitive?.contentOrNull != "completed") return@forEach
            exams++
            pctSum += ((o["result"] as? JsonObject)?.get("percentage") as? JsonPrimitive)?.doubleOrNull ?: 0.0
            questions += ((o["exam"] as? JsonObject)?.get("questions") as? JsonArray)?.size ?: 0
        }
        return QuickStats(exams, if (exams > 0) Math.round(pctSum / exams).toInt() else 0, questions)
    }

    // ---------- settings: AI configuration + exam preferences (same JSON as the website) ----------
    val aiConfig: StateFlow<AiConfig> = namespace
        .flatMapLatest { store.blob(it, LocalStore.AI_CONFIG) }
        .map { parseAiConfig(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AiConfig())

    val examPrefs: StateFlow<ExamPrefs> = namespace
        .flatMapLatest { store.blob(it, LocalStore.EXAM_PREFS) }
        .map { parsePrefs(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ExamPrefs())

    private fun parseObject(raw: String?): JsonObject? =
        if (raw.isNullOrBlank()) null else try { json.parseToJsonElement(raw) as? JsonObject } catch (e: Exception) { null }

    private fun parseAiConfig(raw: String?): AiConfig {
        val o = parseObject(raw) ?: return AiConfig()
        return AiConfig(
            apiKey = (o["apiKey"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            model = (o["model"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
        )
    }

    private fun parsePrefs(raw: String?): ExamPrefs {
        val d = ExamPrefs()
        val o = parseObject(raw) ?: return d
        fun str(k: String, def: String) = (o[k] as? JsonPrimitive)?.contentOrNull ?: def
        fun bool(k: String, def: Boolean) = (o[k] as? JsonPrimitive)?.booleanOrNull ?: def
        return ExamPrefs(
            difficulty = str("difficulty", d.difficulty),
            language = str("language", d.language),
            duration = str("duration", d.duration),
            autosave = bool("autosave", d.autosave),
            showAnswerKey = bool("showAnswerKey", d.showAnswerKey),
            negativeMarkEnabled = bool("negativeMarkEnabled", d.negativeMarkEnabled),
            negativeMarkValue = (o["negativeMarkValue"] as? JsonPrimitive)?.doubleOrNull ?: d.negativeMarkValue,
        )
    }

    /** Settings → Save Settings: writes both documents locally and queues the cloud sync. */
    fun saveSettings(ai: AiConfig, prefs: ExamPrefs) {
        viewModelScope.launch {
            saveDocNow(LocalStore.AI_CONFIG, buildJsonObject {
                put("apiKey", ai.apiKey.trim())
                put("model", ai.model)
            }.toString())
            saveDocNow(LocalStore.EXAM_PREFS, buildJsonObject {
                put("difficulty", prefs.difficulty)
                put("language", prefs.language)
                put("duration", prefs.duration)
                put("autosave", prefs.autosave)
                put("showAnswerKey", prefs.showAnswerKey)
                put("negativeMarkEnabled", prefs.negativeMarkEnabled)
                put("negativeMarkValue", prefs.negativeMarkValue)
            }.toString())
        }
    }

    /** Live Exam: schedule, access, merit and live attempts. Reads the same Supabase tables as the website. */
    val live: LiveExamEngine = LiveExamEngine(
        scope = viewModelScope,
        store = store,
        currentUser = {
            val u = (authManager.state.value as? AuthState.SignedIn)?.user
            val email = u?.email?.lowercase()
            if (u == null || email.isNullOrBlank()) null else LiveUser(email, u.name)
        },
        idToken = {
            runCatching { FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token }.getOrNull()
        },
        isOnline = { ec.network.online.value },
        cacheDir = File(app.filesDir, "live"),
    )

    /** Live Exam admin writes (Courses, Questions, Results, Users, Enrollments). Screens check isLiveAdmin first. */
    val admin: AdminEngine = AdminEngine(
        scope = viewModelScope,
        live = live,
        idToken = {
            runCatching { FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token }.getOrNull()
        },
    )

    /** Practice Admin writes (categories, subjects, topics, questions). Only the practice admin sees the tab. */
    val practiceAdmin: PracticeAdminEngine by lazy {
        PracticeAdminEngine(
            scope = viewModelScope,
            practice = practice,
            idToken = {
                runCatching { FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token }.getOrNull()
            },
        )
    }

    /** Exam flow: generate, take, submit. */
    val exam: ExamEngine = ExamEngine(
        context = app,
        scope = viewModelScope,
        store = store,
        namespace = namespace,
        saveDoc = { name, text -> saveDocNow(name, text) },
        aiConfig = { aiConfig.value },
        liveQuestions = { id -> live.questionsForRestore(id) },
    )

    /** Practice: category tree, question banks, saved progress (practice_progress). */
    val practice: PracticeEngine = PracticeEngine(
        context = app,
        scope = viewModelScope,
        namespace = namespace,
        currentUid = { (authManager.state.value as? AuthState.SignedIn)?.user?.uid },
        onSync = { _sync.value = if (it == SyncStatus.ERROR && !ec.network.online.value) SyncStatus.OFFLINE else it },
        isOnline = { ec.network.online.value },
    )

    init {
        live.refresh()
        viewModelScope.launch {
            authManager.state.collect { st ->
                live.recomputeForUser()
                if (st is AuthState.SignedIn) {
                    live.refresh()
                    pullAndMerge(st)
                } else {
                    live.refresh()
                    _sync.value = SyncStatus.SYNCED
                }
            }
        }
        // Connection changes: show the offline state, and the moment the network returns, catch up.
        viewModelScope.launch {
            var wasOnline = ec.network.online.value
            ec.network.online.collect { now ->
                val st = authManager.state.value as? AuthState.SignedIn
                if (now && !wasOnline) backOnline(st)
                else if (!now && st != null) _sync.value = SyncStatus.OFFLINE
                wasOnline = now
            }
        }
    }

    /** The connection is back: refresh what the screens show and push whatever was saved while offline. */
    private suspend fun backOnline(st: AuthState.SignedIn?) {
        live.refresh()
        practice.refreshManifest()
        if (st != null) {
            practice.retryPending()
            // fresh = false: this is not a new sign-in, so work done as a guest is not folded in again.
            pullAndMerge(st.copy(fresh = false))
        }
    }

    /** Submits the running exam. False means it cannot be submitted yet (see ExamEngine.submit). */
    fun submitExam(): Boolean {
        if (!exam.submit()) return false
        val done = exam.session.value
        if (done != null && done.isCompleted && done.exam.liveExamId.isNotEmpty()) live.reportResult(done)
        return true
    }

    /** Starts a live attempt after prepareJoin() returned Ready. Time runs to the shared deadline, as on the website. */
    fun startLiveExam(ready: JoinResult.Ready): Boolean {
        val remaining = ready.exam.endMs - System.currentTimeMillis()
        if (remaining <= 0) return false
        exam.startExam(
            Exam(
                examId = exam.newExamId(),
                liveExamId = ready.exam.id,
                subject = ready.exam.subject,
                topic = ready.exam.topic,
                language = ready.exam.language,
                questionCount = ready.questions.size,
                duration = ready.exam.durationMinutes,
                marksPerQuestion = ready.exam.marksPerQuestion,
                negativeMarking = ready.exam.negativeMarking,
                timeLimitMs = remaining,
                questions = ready.questions,
            )
        )
        return true
    }

    // ---------- appearance ----------
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { store.setTheme(mode) }
    fun setLang(lang: Lang) = viewModelScope.launch { store.setLang(lang) }

    // ---------- auth ----------
    fun signIn(activity: Activity) {
        viewModelScope.launch {
            authManager.signIn(activity).onFailure { e ->
                if (e !is AuthManager.CancelledException) {
                    _events.tryEmit(e.message ?: "Sign-in failed")
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            flushNow()
            authManager.signOut()
        }
    }

    /** Delete account: erase cloud rows, delete the Firebase user, then wipe this phone's copy of that account. */
    fun deleteAccount(activity: Activity) {
        val uid = (authManager.state.value as? AuthState.SignedIn)?.user?.uid ?: return
        viewModelScope.launch {
            // Nothing queued may be uploaded after the cloud rows are gone: that would bring them back.
            writeJob?.cancel()
            pending.clear()
            val lang = settings.value.lang
            authManager.deleteAccount(activity) { token -> SupabaseApi.deleteAccountData(token) }
                .onSuccess {
                    store.clearNamespace(uid)
                    _events.tryEmit(I18n.get(lang, "auth.deleteModal.done"))
                }
                .onFailure { e ->
                    if (e !is AuthManager.CancelledException) _events.tryEmit(I18n.get(lang, "auth.deleteModal.failed"))
                }
        }
    }

    // ---------- cloud sync (same design as the website: local is the source of truth) ----------
    private var pending: MutableMap<String, JsonElement> = mutableMapOf()
    private var writeJob: Job? = null

    // Offline sync queue. `pending` is the in-memory upload batch; the dirty list is its copy on disk.
    // It tells the next sign-in or reconnect which local documents must win over the cloud copy.
    private val dirtyKnown = HashSet<String>()
    private val dirtyMutex = Mutex()

    private suspend fun readDirty(ns: String): Set<String> =
        parseArray(store.blobOnce(ns, LocalStore.SYNC_DIRTY))
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .toSet()

    private suspend fun updateDirty(ns: String, add: Collection<String> = emptyList(), remove: Collection<String> = emptyList()) {
        dirtyMutex.withLock {
            val next = (readDirty(ns) + add) - remove.toSet()
            store.setBlob(
                ns,
                LocalStore.SYNC_DIRTY,
                if (next.isEmpty()) null else JsonArray(next.map { JsonPrimitive(it) }).toString(),
            )
        }
    }

    /** The cloud confirmed these documents. Names with a newer change still waiting stay marked. */
    private suspend fun clearDirty(uid: String, names: Collection<String>) {
        val settled = names.filter { it !in pending }
        if (settled.isEmpty()) return
        settled.forEach { dirtyKnown.remove("$uid|$it") }
        updateDirty(uid, remove = settled)
    }

    private fun failedStatus(): SyncStatus =
        if (ec.network.online.value) SyncStatus.ERROR else SyncStatus.OFFLINE

    /** Saves a JSON document for the current account locally and mirrors it up, debounced like the website. */
    fun saveDoc(name: String, jsonText: String) {
        viewModelScope.launch { saveDocNow(name, jsonText) }
    }

    suspend fun saveDocNow(name: String, jsonText: String) {
        store.setBlob(namespace.value, name, jsonText)
        if (authManager.state.value is AuthState.SignedIn) {
            // Remember on disk that the cloud has not seen this change yet, so a restart cannot lose it.
            val ns = namespace.value
            if (dirtyKnown.add("$ns|$name")) updateDirty(ns, add = listOf(name))
            pending[name] = json.parseToJsonElement(jsonText)
            _sync.value = SyncStatus.SYNCING
            writeJob?.cancel()
            writeJob = viewModelScope.launch {
                delay(600)
                flushNow()
            }
        }
    }

    fun flushAll() {
        exam.flush()
        practice.flush()
        flushNow()
    }

    fun flushNow() {
        val st = authManager.state.value as? AuthState.SignedIn ?: return
        if (pending.isEmpty()) return
        val patch = pending
        pending = mutableMapOf()
        viewModelScope.launch {
            try {
                SupabaseApi.upsertUserData(st.user.uid, st.user.email, patch)
                clearDirty(st.user.uid, patch.keys)
                _sync.value = SyncStatus.SYNCED
            } catch (e: Exception) {
                patch.forEach { (k, v) -> pending.putIfAbsent(k, v) }
                _sync.value = failedStatus()
            }
        }
    }

    private suspend fun pullAndMerge(st: AuthState.SignedIn) {
        _sync.value = SyncStatus.SYNCING
        val uid = st.user.uid
        val dirty = readDirty(uid)
        val pendingBefore = HashMap(pending)
        try {
            val cloud: CloudDoc? = SupabaseApi.fetchUserData(uid)
            // A setting changed on this phone and not uploaded yet (for example while offline) must not be
            // overwritten by the older cloud copy. It is pushed up below instead.
            if (LocalStore.AI_CONFIG !in dirty) cloud?.aiConfig?.let { store.setBlob(uid, LocalStore.AI_CONFIG, it.toString()) }
            if (LocalStore.EXAM_PREFS !in dirty) cloud?.examPrefs?.let { store.setBlob(uid, LocalStore.EXAM_PREFS, it.toString()) }

            // Merge exam history by sessionId, newest updatedAt wins. Work done while signed out
            // ("guest") is folded in only on a fresh sign-in, then cleared so it can never leak
            // into a different account later.
            val local = parseArray(store.blobOnce(uid, LocalStore.EXAM_HISTORY))
            val cloudHistory = (cloud?.examHistory as? JsonArray)?.toList().orEmpty()
            var merged = mergeHistories(local, cloudHistory)
            if (st.fresh) {
                val guest = parseArray(store.blobOnce(LocalStore.GUEST, LocalStore.EXAM_HISTORY))
                if (guest.isNotEmpty()) {
                    merged = mergeHistories(merged, guest)
                    store.setBlob(LocalStore.GUEST, LocalStore.EXAM_HISTORY, null)
                }
            }
            val mergedJson = JsonArray(capHistory(merged, ExamEngine.HISTORY_MAX, System.currentTimeMillis()))
            store.setBlob(uid, LocalStore.EXAM_HISTORY, mergedJson.toString())

            // Push the merged view back so Supabase has the full picture too.
            val patch = mutableMapOf<String, JsonElement>(LocalStore.EXAM_HISTORY to mergedJson)
            store.blobOnce(uid, LocalStore.AI_CONFIG)?.let { patch[LocalStore.AI_CONFIG] = json.parseToJsonElement(it) }
            store.blobOnce(uid, LocalStore.EXAM_PREFS)?.let { patch[LocalStore.EXAM_PREFS] = json.parseToJsonElement(it) }
            SupabaseApi.upsertUserData(uid, st.user.email, patch)
            // The merged copy now holds everything queued before this pull. Drop those queue entries,
            // but keep any change saved while the pull was running (it is a different object).
            pendingBefore.forEach { (k, v) -> if (pending[k] === v) pending.remove(k) }
            clearDirty(uid, patch.keys)
            _sync.value = SyncStatus.SYNCED
        } catch (e: Exception) {
            _sync.value = failedStatus()
        }
        // Practice progress lives in its own table; a failure here must not undo the sync status above.
        try {
            practice.onSignedIn(uid, st.fresh)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _sync.value = failedStatus()
        }
    }

    private fun parseArray(raw: String?): List<JsonElement> =
        if (raw.isNullOrBlank()) emptyList()
        else try { (json.parseToJsonElement(raw) as? JsonArray)?.toList().orEmpty() } catch (e: Exception) { emptyList() }

    private fun updatedAt(el: JsonElement): Double =
        ((el as? JsonObject)?.get("updatedAt") as? JsonPrimitive)?.doubleOrNull ?: 0.0
}
