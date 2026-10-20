package com.examcamp.app.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.examcamp.app.AppViewModel
import com.examcamp.app.auth.AuthState
import com.examcamp.app.data.JoinResult
import com.examcamp.app.ui.screens.LiveExamScreen
import com.examcamp.app.ui.screens.admin.AdminScreen
import com.examcamp.app.ui.screens.HistoryScreen
import com.examcamp.app.ui.screens.StatisticsScreen
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.examcamp.app.ui.components.SiteHeader
import com.examcamp.app.data.GenState
import com.examcamp.app.i18n.t
import com.examcamp.app.ui.screens.AiFormState
import com.examcamp.app.ui.screens.ExamScreen
import com.examcamp.app.ui.screens.GeneratingScreen
import com.examcamp.app.ui.screens.HomeScreen
import com.examcamp.app.ui.screens.PracticeCategoriesScreen
import com.examcamp.app.ui.screens.PracticeModeScreen
import com.examcamp.app.ui.screens.PracticeSubjectsScreen
import com.examcamp.app.ui.screens.ResultsScreen
import androidx.compose.runtime.LaunchedEffect
import com.examcamp.app.ui.screens.SettingsSheet
import com.examcamp.app.ui.theme.Ec
import com.examcamp.app.ui.theme.ThemeMode
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.examcamp.app.ui.components.OfflineBanner

private enum class Screen { HOME, GENERATING, EXAM, RESULTS, PRACTICE_CATEGORIES, PRACTICE_SUBJECTS, PRACTICE_MODE, LIVE, HISTORY, STATISTICS, REVIEW, ADMIN }

@Composable
fun AppRoot(vm: AppViewModel, activity: Activity, dark: Boolean) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val auth by vm.auth.collectAsStateWithLifecycle()
    val membersNow by vm.live.members.collectAsStateWithLifecycle()
    val myEmail = (auth as? com.examcamp.app.auth.AuthState.SignedIn)?.user?.email?.lowercase()
    val isPracticeAdminUser = myEmail != null && myEmail in com.examcamp.app.Config.PRACTICE_ADMIN_EMAILS
    val adminVisible = remember(membersNow, auth) {
        vm.live.isLiveAdmin((auth as? com.examcamp.app.auth.AuthState.SignedIn)?.user?.email?.lowercase())
    }
    val sync by vm.sync.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val quick by vm.quickStats.collectAsStateWithLifecycle()
    val aiConfig by vm.aiConfig.collectAsStateWithLifecycle()
    val prefs by vm.examPrefs.collectAsStateWithLifecycle()
    val session by vm.exam.session.collectAsStateWithLifecycle()
    val gen by vm.exam.gen.collectAsStateWithLifecycle()
    val practiceCategories by vm.practice.categories.collectAsStateWithLifecycle()
    val practiceRun by vm.practice.run.collectAsStateWithLifecycle()
    var practiceCategoryId by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var review by remember { mutableStateOf<com.examcamp.app.data.Session?>(null) }
    val aiForm = remember { AiFormState() }
    val context = LocalContext.current
    val timeUpMessage = t("exam.timeUp")
    val liveScope = rememberCoroutineScope()
    val liveSignIn = t("live.toast.signIn")
    val liveNotEnrolled = t("live.toast.notEnrolled")
    val liveNotLive = t("live.toast.notLive")
    val liveSubmitted = t("live.toast.submitted")
    val liveNoQuestions = t("live.toast.noQuestions")
    val liveFailed = t("live.toast.failed")
    val needOnline = t("net.needOnline")
    val liveLateJoin = t("live.toast.lateJoin")
    fun liveToast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    // Generation finished: open the exam. Failure stays on the Generating screen with its error card.
    LaunchedEffect(gen) {
        if (gen is GenState.Done) {
            screen = Screen.EXAM
            vm.exam.resetGen()
        }
    }

    // On a tablet the content stays a readable 720 dp column in the middle; phones are unaffected.
    Box(
        Modifier
            .fillMaxSize()
            .background(Ec.c.bg)
            .navigationBarsPadding()
            .imePadding(),
    ) {
    Column(Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxSize()) {
    AnimatedContent(
        targetState = screen,
        modifier = Modifier.weight(1f),
        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
        label = "screen",
    ) { target ->
    Column(Modifier.fillMaxSize()) {
        when (target) {
            Screen.HOME -> {
                SiteHeader(
                    dark = dark,
                    lang = settings.lang,
                    auth = auth,
                    sync = sync,
                    onToggleTheme = { vm.setTheme(if (dark) ThemeMode.LIGHT else ThemeMode.DARK) },
                    onLangChange = { vm.setLang(it) },
                    onOpenSettings = { showSettings = true },
                    onSignIn = { vm.signIn(activity) },
                    onSignOut = { vm.signOut() },
                    onDeleteAccount = { vm.deleteAccount(activity) },
                )
                HomeScreen(
                    quick = quick,
                    session = session,
                    aiForm = aiForm,
                    prefs = prefs,
                    showAdmin = adminVisible,
                    onNavigate = { target ->
                        if (target == "practice") screen = Screen.PRACTICE_CATEGORIES
                        else if (target == "live-exam") screen = Screen.LIVE
                        else if (target == "history") screen = Screen.HISTORY
                        else if (target == "admin" && adminVisible) screen = Screen.ADMIN
                        else if (target == "statistics") screen = Screen.STATISTICS
                        else Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show()
                    },
                    onGenerate = { config, attachments ->
                        // AI generation needs the network: say so now instead of failing after a wait.
                        if (!online) {
                            liveToast(needOnline)
                        } else {
                            vm.exam.generate(config, attachments)
                            screen = Screen.GENERATING
                        }
                    },
                    onOpenSession = {
                        screen = if (session?.isCompleted == true) Screen.RESULTS else Screen.EXAM
                    },
                )
            }
            Screen.GENERATING -> GeneratingScreen(
                state = gen,
                onRetry = { vm.exam.retry() },
                onSample = { vm.exam.retry(sample = true) },
                onBack = {
                    vm.exam.cancelGeneration()
                    screen = Screen.HOME
                },
            )
            Screen.EXAM -> {
                val s = session
                if (s == null || s.isCompleted) {
                    // Nothing to take (cleared, or already submitted): fall back instead of showing an empty screen.
                    LaunchedEffect(s?.isCompleted) { screen = if (s?.isCompleted == true) Screen.RESULTS else Screen.HOME }
                } else {
                    ExamScreen(
                        session = s,
                        onAnswer = { id, idx -> vm.exam.selectAnswer(id, idx) },
                        onMark = { vm.exam.toggleMark(it) },
                        onGoTo = { vm.exam.goTo(it) },
                        onSubmit = {
                            if (vm.submitExam()) screen = Screen.RESULTS
                        },
                        onTimeUp = {
                            if (vm.submitExam()) {
                                Toast.makeText(context, timeUpMessage, Toast.LENGTH_LONG).show()
                                screen = Screen.RESULTS
                            }
                        },
                        onBack = { screen = Screen.HOME },
                    )
                }
            }
            Screen.PRACTICE_CATEGORIES -> {
                // Pick up admin changes to the tree whenever the Practice panel opens; failures keep the cached tree.
                LaunchedEffect(Unit) { vm.practice.refreshManifest() }
                PracticeCategoriesScreen(
                    categories = practiceCategories,
                    onPick = { id ->
                        practiceCategoryId = id
                        screen = Screen.PRACTICE_SUBJECTS
                    },
                    onHome = { screen = Screen.HOME },
                    onBack = { screen = Screen.HOME },
                )
            }
            Screen.PRACTICE_SUBJECTS -> {
                val category = practiceCategories.find { it.id == practiceCategoryId }
                if (category == null) {
                    LaunchedEffect(Unit) { screen = Screen.PRACTICE_CATEGORIES }
                } else {
                    PracticeSubjectsScreen(
                        category = category,
                        engine = vm.practice,
                        onStartPractice = { screen = Screen.PRACTICE_MODE },
                        onStartEvaluate = { subject, topic, module, number ->
                            // Evaluate reuses the Exam Mode player: timer, submit, score, review.
                            val exam = vm.practice.buildEvaluateExam(
                                subject, topic, module, number,
                                negativeMarking = prefs.negativeMarkValue,
                                examId = vm.exam.newExamId(),
                            )
                            vm.exam.startExam(exam)
                            screen = Screen.EXAM
                        },
                        onHome = { screen = Screen.HOME },
                        onBack = { screen = Screen.PRACTICE_CATEGORIES },
                    )
                }
            }
            Screen.PRACTICE_MODE -> {
                val r = practiceRun
                if (r == null) {
                    LaunchedEffect(Unit) { screen = Screen.PRACTICE_SUBJECTS }
                } else {
                    PracticeModeScreen(
                        run = r,
                        onAnswer = { id, idx -> vm.practice.answer(id, idx) },
                        onSetCurrent = { vm.practice.setCurrent(it) },
                        onBack = {
                            vm.practice.closeRun()
                            screen = Screen.PRACTICE_SUBJECTS
                        },
                    )
                }
            }
            Screen.HISTORY -> {
                val historyRaw by vm.examHistory.collectAsStateWithLifecycle()
                val liveNow by vm.live.exams.collectAsStateWithLifecycle()
                val ownScores by vm.live.myScores.collectAsStateWithLifecycle()
                val recordNoOpenMsg = t("record.noOpen")
                HistoryScreen(
                    practice = com.examcamp.app.data.practiceRows(historyRaw),
                    live = com.examcamp.app.data.liveRecords(liveNow, ownScores, System.currentTimeMillis()),
                    onOpenPractice = { row ->
                        val s = com.examcamp.app.data.decodeSessionRecord(row.raw)
                        if (s != null && s.isCompleted) {
                            review = s
                            screen = Screen.REVIEW
                        } else {
                            liveToast(recordNoOpenMsg)
                        }
                    },
                    onDeletePractice = { row -> liveScope.launch { vm.deleteHistoryRecord(row.sessionId) } },
                    onUndoDelete = { row -> liveScope.launch { vm.restoreHistoryRecord(row.raw) } },
                    onHome = { screen = Screen.HOME },
                    onBack = { screen = Screen.HOME },
                )
            }
            Screen.STATISTICS -> {
                val historyRaw by vm.examHistory.collectAsStateWithLifecycle()
                val liveNow by vm.live.exams.collectAsStateWithLifecycle()
                val ownScores by vm.live.myScores.collectAsStateWithLifecycle()
                val progressNow by vm.practice.progress.collectAsStateWithLifecycle()
                StatisticsScreen(
                    practice = com.examcamp.app.data.practiceRows(historyRaw),
                    live = com.examcamp.app.data.liveRecords(liveNow, ownScores, System.currentTimeMillis()),
                    categories = practiceCategories,
                    progress = progressNow,
                    onHome = { screen = Screen.HOME },
                    onBack = { screen = Screen.HOME },
                )
            }
            Screen.REVIEW -> {
                val rv = review
                if (rv != null) {
                    ResultsScreen(rv, onHome = { screen = Screen.HISTORY })
                } else {
                    screen = Screen.HISTORY
                }
            }
            Screen.ADMIN -> {
                AdminScreen(
                    live = vm.live,
                    admin = vm.admin,
                    padmin = vm.practiceAdmin,
                    isPracticeAdmin = isPracticeAdminUser,
                    onHome = { screen = Screen.HOME },
                    onBack = { screen = Screen.HOME },
                )
            }
            Screen.LIVE -> {
                LiveExamScreen(
                    engine = vm.live,
                    onJoin = { exam ->
                        if (auth !is AuthState.SignedIn) {
                            liveToast(liveSignIn)
                        } else {
                            liveScope.launch {
                                when (val r = vm.live.prepareJoin(exam)) {
                                    is JoinResult.Ready -> {
                                        if (vm.startLiveExam(r)) screen = Screen.EXAM else liveToast(liveLateJoin)
                                    }
                                    JoinResult.NotEnrolled -> liveToast(liveNotEnrolled)
                                    JoinResult.NotLive -> liveToast(liveNotLive)
                                    JoinResult.AlreadySubmitted -> liveToast(liveSubmitted)
                                    JoinResult.NoQuestions -> liveToast(liveNoQuestions)
                                    is JoinResult.Failed -> liveToast(liveFailed)
                                }
                            }
                        }
                    },
                    onHome = { screen = Screen.HOME },
                    onBack = { screen = Screen.HOME },
                )
            }
            Screen.RESULTS -> {
                val s = session
                if (s?.result == null) {
                    LaunchedEffect(Unit) { screen = Screen.HOME }
                } else {
                    ResultsScreen(session = s, onHome = { screen = Screen.HOME })
                }
            }
        }
    }
    }
    OfflineBanner(online = online)
    }
    }

    if (showSettings) {
        SettingsSheet(
            theme = settings.theme,
            lang = settings.lang,
            onTheme = { vm.setTheme(it) },
            onLang = { vm.setLang(it) },
            aiConfig = aiConfig,
            prefs = prefs,
            onSave = { ai, p -> vm.saveSettings(ai, p) },
            onDismiss = { showSettings = false },
        )
    }
}
