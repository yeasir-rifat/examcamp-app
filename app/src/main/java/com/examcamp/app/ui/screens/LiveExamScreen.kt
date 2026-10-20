package com.examcamp.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.examcamp.app.data.EnrollFormState
import com.examcamp.app.data.EnrollmentDraft
import com.examcamp.app.data.LiveExam
import com.examcamp.app.data.LiveExamEngine
import com.examcamp.app.data.LiveSubject
import com.examcamp.app.data.LiveSummary
import com.examcamp.app.data.MeritRow
import com.examcamp.app.i18n.t
import com.examcamp.app.i18n.tf
import com.examcamp.app.ui.components.EcButton
import com.examcamp.app.ui.components.EcCard
import com.examcamp.app.ui.components.EcDropdown
import com.examcamp.app.ui.components.EcFormGroup
import com.examcamp.app.ui.components.EcIcon
import com.examcamp.app.ui.components.EcTextField
import com.examcamp.app.ui.theme.Ec
import com.examcamp.app.ui.theme.EcIcons
import com.examcamp.app.ui.theme.Radius
import com.examcamp.app.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.examcamp.app.ui.components.LiveSkeleton
import com.examcamp.app.ui.components.swipeTabs

/**
 * Live Exam page (#view-live-exam on the website): campaign cards with countdowns, then the
 * Routine, Subjects and Merit tabs, plus the public "Enroll Now" form.
 * Live data comes from [engine]; joining is handed back to AppRoot through [onJoin].
 */
@Composable
fun LiveExamScreen(
    engine: LiveExamEngine,
    onJoin: (LiveExam) -> Unit,
    onHome: () -> Unit,
    onBack: () -> Unit,
) {
    val c = Ec.c
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exams by engine.exams.collectAsStateWithLifecycle()
    val subjects by engine.subjects.collectAsStateWithLifecycle()
    val members by engine.members.collectAsStateWithLifecycle()
    val merit by engine.merit.collectAsStateWithLifecycle()
    val summary by engine.summary.collectAsStateWithLifecycle()
    val loaded by engine.loaded.collectAsStateWithLifecycle()
    val fromCache by engine.fromCache.collectAsStateWithLifecycle()
    val submitted by engine.submittedIds.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var enrollOpen by remember { mutableStateOf(false) }
    var enrollPreselect by remember { mutableStateOf<String?>(null) }

    // Realtime (same channel as the website) reloads data on every change while this screen is open.
    DisposableEffect(Unit) {
        engine.startRealtime()
        engine.refresh()
        onDispose { engine.stopRealtime() }
    }
    // A 1 s clock drives every countdown. A 60 s reload is a safety net if the socket drops.
    LaunchedEffect(Unit) {
        var sinceReload = 0L
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
            sinceReload += 1000
            if (sinceReload >= 60_000L) {
                sinceReload = 0L
                engine.refresh()
            }
        }
    }

    fun hasAccess(subject: String): Boolean = engine.hasSubjectAccess(subject)
    fun openEnroll(subject: String?) {
        enrollPreselect = subject
        enrollOpen = true
    }

    val upcoming = exams
        .filter { (it.status == "scheduled" || it.status == "live") && now < it.endMs }
        .sortedBy { it.startMs }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        SubpageHeader(t("home.dashboard.liveTitle"), onHome, onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            if (!loaded) {
                LiveSkeleton()
                return@Column
            }
            if (fromCache) {
                Text(t("net.cachedNote"), style = Ec.t.small, color = c.textMuted)
            }
            // Campaign cards: one per scheduled or live exam, visible to everyone; Join needs access.
            if (upcoming.isEmpty()) {
                Text(t("live.card.empty"), style = Ec.t.small, color = c.textSecondary)
            } else {
                upcoming.forEach { exam ->
                    CampaignCard(
                        exam = exam,
                        now = now,
                        hasAccess = hasAccess(exam.subject),
                        submitted = exam.id in submitted,
                        onJoin = { onJoin(exam) },
                        onEnroll = { openEnroll(exam.subject) },
                    )
                }
            }

            SummaryCard(summary)

            LiveTabs(tab) { tab = it }

            Box(Modifier.fillMaxWidth().swipeTabs(tab, 3) { tab = it }) {
                when (tab) {
                    0 -> RoutineTab(exams, now, ::hasAccess)
                    1 -> SubjectsTab(subjects, exams, ::hasAccess, onEnroll = { openEnroll(it) })
                    else -> MeritTab(merit, exams, subjects, ::hasAccess)
                }
            }
        }
    }

    if (enrollOpen) {
        EnrollSheet(
            subjects = subjects,
            preselect = enrollPreselect,
            onDismiss = { enrollOpen = false },
            onSubmit = { draft -> engine.submitEnrollment(draft) },
            onLoadDraft = { engine.loadEnrollDraft() },
            onSaveDraft = { engine.saveEnrollDraft(it) },
            onSent = {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            },
            scope = scope,
            failedText = t("live.enroll.failed"),
            thanksText = t("live.enroll.thanks"),
            invalidText = t("live.enroll.invalid"),
            sendingText = t("live.enroll.sending"),
        )
    }
}

// ---------- campaign card ----------

@Composable
private fun CampaignCard(
    exam: LiveExam,
    now: Long,
    hasAccess: Boolean,
    submitted: Boolean,
    onJoin: () -> Unit,
    onEnroll: () -> Unit,
) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.lg)
    val live = exam.isLiveAt(now)
    val remaining = if (live) exam.endMs - now else exam.startMs - now
    val totalMarks = exam.marksPerQuestion * exam.questionCount
    val marksText = if (exam.questionCount > 0) "${fmtNum(totalMarks)} ${t("live.card.marks")}" else t("live.common.tba")
    val meta = "${dateTime(exam.startMs)} · ${exam.durationMinutes} ${t("live.card.minShort")} · $marksText"

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, if (hasAccess) c.primaryBorder else c.border, shape)
            .padding(Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            Text(
                exam.subject,
                style = Ec.t.h4,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (live) LiveBadge()
        }
        Text(exam.topic, style = Ec.t.body, color = c.textSecondary)
        Text(meta, style = Ec.t.caption, color = c.textMuted)
        Text(
            if (live) t("live.card.remaining") else t("live.card.upcoming"),
            style = Ec.t.label,
            color = c.textPrimary,
        )
        Countdown(remaining)

        when {
            !hasAccess -> EcButton(t("live.card.enroll"), onClick = onEnroll, modifier = Modifier.fillMaxWidth())
            submitted -> EcButton(t("live.card.submitted"), onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false)
            live -> EcButton(t("live.card.join"), onClick = onJoin, modifier = Modifier.fillMaxWidth())
            else -> EcButton(t("live.card.notStarted"), onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false)
        }
    }
}

@Composable
private fun Countdown(remainingMs: Long) {
    val c = Ec.c
    val total = remainingMs.coerceAtLeast(0L)
    val parts = listOf(
        total / 86_400_000L to t("live.card.days"),
        (total / 3_600_000L) % 24 to t("live.card.hours"),
        (total / 60_000L) % 60 to t("live.card.min"),
        (total / 1000L) % 60 to t("live.card.sec"),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
        parts.forEach { (value, label) ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(c.surfaceAlt)
                    .padding(vertical = Space.s2),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(String.format(Locale.US, "%02d", value), style = Ec.t.h3, color = c.textPrimary)
                Text(label, style = Ec.t.caption, color = c.textMuted)
            }
        }
    }
}

@Composable
private fun LiveBadge() {
    val c = Ec.c
    Row(
        Modifier
            .clip(CircleShape)
            .background(c.dangerLight)
            .border(1.dp, c.dangerBorder, CircleShape)
            .padding(horizontal = Space.s2, vertical = Space.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s1),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(c.danger))
        Text(t("live.common.live"), style = Ec.t.caption, color = c.danger)
    }
}

@Composable
private fun StatusPill(text: String, color: Color, background: Color, border: Color) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(background)
            .border(1.dp, border, CircleShape)
            .padding(horizontal = Space.s2, vertical = Space.s1),
    ) {
        Text(text, style = Ec.t.caption, color = color)
    }
}

// ---------- summary and tabs ----------

@Composable
private fun SummaryCard(summary: LiveSummary) {
    val c = Ec.c
    EcCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SummaryStat(if (summary.rank > 0) "#${summary.rank}" else "-", t("live.summary.rank"), Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(40.dp).background(c.border))
            SummaryStat(summary.given.toString(), t("live.summary.given"), Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(40.dp).background(c.border))
            SummaryStat("${fmtNum(summary.avgPercent)}%", t("live.summary.avg"), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, modifier: Modifier) {
    val c = Ec.c
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s1)) {
        Text(value, style = Ec.t.h3, color = c.textPrimary)
        Text(label, style = Ec.t.caption, color = c.textMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun LiveTabs(selected: Int, onSelect: (Int) -> Unit) {
    val c = Ec.c
    val tabs = listOf("live.tab.routine", "live.tab.subjects", "live.tab.merit")
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { i, key ->
                val on = i == selected
                val tint = if (on) c.primary else c.textSecondary
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(role = Role.Tab) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                        Text(t(key), style = Ec.t.smallStrong, color = tint, maxLines = 1)
                    }
                    Box(Modifier.fillMaxWidth().height(2.dp).background(if (on) c.primary else Color.Transparent))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

// ---------- Routine ----------

@Composable
private fun RoutineTab(exams: List<LiveExam>, now: Long, hasAccess: (String) -> Boolean) {
    val c = Ec.c
    var openGroup by rememberSaveable { mutableStateOf<String?>(null) }
    if (exams.isEmpty()) {
        Text(t("live.routine.empty"), style = Ec.t.small, color = c.textSecondary)
        return
    }
    val groups = exams.sortedBy { it.startMs }.groupBy { it.subject }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        groups.forEach { (subject, list) ->
            val isOpen = openGroup == subject
            val liveNow = list.any { it.isLiveAt(now) }
            val upcomingCount = list.count { now < it.startMs }
            val subtitle = buildList {
                add(tf("live.routine.topics", list.size))
                when {
                    liveNow -> add(t("live.routine.liveNow"))
                    upcomingCount > 0 -> add(tf("live.routine.upcomingCount", upcomingCount))
                    else -> add(t("live.routine.allEnded"))
                }
            }.joinToString(" · ")
            val shape = RoundedCornerShape(Radius.lg)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(c.surface)
                    .border(1.dp, if (hasAccess(subject)) c.primaryBorder else c.border, shape),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { openGroup = if (isOpen) null else subject }
                        .padding(Space.s4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s2),
                ) {
                    if (liveNow) Box(Modifier.size(8.dp).clip(CircleShape).background(c.danger))
                    Column(Modifier.weight(1f)) {
                        Text(subject, style = Ec.t.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, style = Ec.t.caption, color = c.textMuted)
                    }
                    EcIcon(EcIcons.ChevronDown, c.textSecondary, 18.dp, Modifier.rotate(if (isOpen) 180f else 0f))
                }
                if (isOpen) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = Space.s4).padding(bottom = Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                        list.forEach { exam -> RoutineRow(exam, now) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineRow(exam: LiveExam, now: Long) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.md)
    val live = exam.isLiveAt(now)
    val over = exam.isOver(now)
    val d = Date(exam.startMs)
    val day = SimpleDateFormat("d", Locale.US).format(d)
    val month = SimpleDateFormat("MMM", Locale.US).format(d)
    val questions = if (exam.questionCount > 0) tf("live.routine.questions", exam.questionCount) else t("live.common.tba")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surfaceAlt)
            .padding(Space.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp)) {
            Text(day, style = Ec.t.h4, color = c.textPrimary)
            Text(month, style = Ec.t.caption, color = c.textMuted)
        }
        Column(Modifier.weight(1f)) {
            Text(exam.topic, style = Ec.t.bodyStrong, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${time(exam.startMs)} · ${exam.durationMinutes} ${t("live.card.minShort")} · $questions",
                style = Ec.t.caption,
                color = c.textMuted,
            )
        }
        when {
            live -> LiveBadge()
            over -> StatusPill(t("live.common.ended"), c.textSecondary, c.surface, c.border)
            else -> StatusPill(t("live.common.upcoming"), c.info, c.infoLight, c.infoBorder)
        }
    }
}

// ---------- Subjects ----------

@Composable
private fun SubjectsTab(
    subjects: List<LiveSubject>,
    exams: List<LiveExam>,
    hasAccess: (String) -> Boolean,
    onEnroll: (String) -> Unit,
) {
    val c = Ec.c
    if (subjects.isEmpty()) {
        Text(t("live.subjects.empty"), style = Ec.t.small, color = c.textSecondary)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        subjects.forEach { s ->
            val count = exams.count { it.subject == s.name }
            val access = hasAccess(s.name)
            val shape = RoundedCornerShape(Radius.lg)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(c.surface)
                    .border(1.dp, if (access) c.primaryBorder else c.border, shape)
                    .padding(Space.s4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s3),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = Ec.t.bodyStrong, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(tf("live.subjects.count", count), style = Ec.t.caption, color = c.textMuted)
                }
                if (access) {
                    StatusPill(t("live.subjects.enrolled"), c.success, c.successLight, c.successBorder)
                } else {
                    EcButton(t("live.card.enroll"), onClick = { onEnroll(s.name) }, small = true)
                }
            }
        }
    }
}

// ---------- Merit ----------

@Composable
private fun MeritTab(
    allRows: List<MeritRow>,
    exams: List<LiveExam>,
    subjects: List<LiveSubject>,
    hasAccess: (String) -> Boolean,
) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.lg)
    // Courses the member may see that have at least one published exam.
    val courses = subjects.map { it.name }.filter { n -> hasAccess(n) && exams.any { it.subject == n && it.published } }
    var course by rememberSaveable { mutableStateOf("") }
    var examId by rememberSaveable { mutableStateOf("") }
    if (course !in courses) course = courses.firstOrNull() ?: ""
    val courseExams = exams.filter { it.subject == course && it.published }.sortedByDescending { it.startMs }
    if (courseExams.none { it.id == examId }) examId = courseExams.firstOrNull()?.id ?: ""
    val rows = allRows.filter { it.examId == examId }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.border, shape)
            .padding(Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        Text(t("live.merit.heading"), style = Ec.t.h4, color = c.textPrimary)
        if (courses.isNotEmpty()) {
            // Same gap the website's filter grid uses (space-4), then a little air before the list.
            Column(
                Modifier.fillMaxWidth().padding(top = Space.s2, bottom = Space.s2),
                verticalArrangement = Arrangement.spacedBy(Space.s4),
            ) {
                EcFormGroup(t("live.merit.course")) {
                    EcDropdown(options = courses.map { it to it }, selected = course, onSelect = { course = it; examId = "" })
                }
                EcFormGroup(t("live.merit.exam")) {
                    EcDropdown(
                        options = courseExams.map { it.id to "${it.topic} · ${SimpleDateFormat("d MMM", Locale.US).format(Date(it.startMs))}" },
                        selected = examId,
                        onSelect = { examId = it },
                    )
                }
            }
        }
        if (rows.isEmpty()) {
            Text(t(if (courses.isEmpty()) "live.merit.empty" else "live.merit.noneForExam"), style = Ec.t.small, color = c.textSecondary)
        }
        rows.forEachIndexed { i, row ->
            val rank = i + 1
            val rankColor = when (rank) {
                1 -> c.warning
                2 -> c.textSecondary
                3 -> c.primary
                else -> c.textMuted
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(if (row.self) c.primaryWash else Color.Transparent)
                    .padding(horizontal = Space.s2, vertical = Space.s2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s3),
            ) {
                Text(rank.toString(), style = Ec.t.bodyStrong, color = rankColor, modifier = Modifier.width(32.dp))
                Text(
                    row.name,
                    style = if (row.self) Ec.t.bodyStrong else Ec.t.body,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("${fmtNum(row.score)}%", style = Ec.t.bodyStrong, color = c.primary)
            }
        }
    }
}

// ---------- Enroll sheet (public form, same fields and checks as the website popup) ----------

@Composable
private fun EnrollSheet(
    subjects: List<LiveSubject>,
    preselect: String?,
    onDismiss: () -> Unit,
    onSubmit: suspend (EnrollmentDraft) -> Boolean,
    onLoadDraft: suspend () -> EnrollFormState?,
    onSaveDraft: (EnrollFormState?) -> Unit,
    onSent: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    failedText: String,
    thanksText: String,
    invalidText: String,
    sendingText: String,
) {
    val c = Ec.c
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var txn by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("Bkash") }
    var courses by remember { mutableStateOf<Set<String>>(emptySet()) }
    var attempted by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var restored by remember { mutableStateOf(false) }

    // Load the saved draft once, then add the course the member tapped on a card.
    LaunchedEffect(Unit) {
        val saved = onLoadDraft()
        if (saved != null) {
            name = saved.name
            phone = saved.phone
            email = saved.email
            txn = saved.txn
            provider = saved.provider
            courses = saved.courses.toSet()
        }
        if (preselect != null) courses = courses + preselect
        restored = true
    }
    // Save every change. Saving waits until the draft is loaded, so an empty form never overwrites it.
    val snapshot = EnrollFormState(name, phone, email, txn, provider, courses.toList())
    LaunchedEffect(snapshot, restored) {
        if (restored) onSaveDraft(snapshot)
    }

    val emailOk = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim())
    val errName = attempted && name.isBlank()
    val errPhone = attempted && phone.isBlank()
    val errEmail = attempted && !emailOk
    val errCourses = attempted && courses.isEmpty()
    val errTxn = attempted && txn.isBlank()
    val valid = name.isNotBlank() && phone.isNotBlank() && emailOk && courses.isNotEmpty() && txn.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.s4)
                .padding(bottom = Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            Text(t("enroll.title"), style = Ec.t.h3, color = c.textPrimary)

            EcFormGroup(t("enroll.name"), error = if (errName) t("enroll.nameError") else null) {
                EcTextField(name, { name = it }, placeholder = "Your full name", isError = errName)
            }
            EcFormGroup(t("enroll.phone"), error = if (errPhone) t("enroll.phoneError") else null) {
                EcTextField(phone, { phone = it }, placeholder = "e.g. 01XXXXXXXXX", isError = errPhone, keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
            }
            EcFormGroup(t("enroll.email"), error = if (errEmail) t("enroll.emailError") else null) {
                EcTextField(email, { email = it }, placeholder = "e.g. name@gmail.com", isError = errEmail, keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
            }

            EcFormGroup(t("enroll.coursesLabel"), error = if (errCourses) t("enroll.coursesError") else null) {
                Column {
                    if (subjects.isEmpty()) {
                        Text(t("live.subjects.empty"), style = Ec.t.small, color = c.textSecondary)
                    }
                    subjects.forEach { s ->
                        val checked = s.name in courses
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Checkbox) {
                                    courses = if (checked) courses - s.name else courses + s.name
                                }
                                .padding(vertical = Space.s1),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = c.primary),
                            )
                            Text(s.name, style = Ec.t.body, color = c.textPrimary)
                        }
                    }
                }
            }

            EcFormGroup(t("enroll.sendMoney")) {
                EcDropdown(
                    options = listOf("Bkash" to "Bkash", "Rocket" to "Rocket", "Nagad" to "Nagad", "Upay" to "Upay"),
                    selected = provider,
                    onSelect = { provider = it },
                )
            }
            EcFormGroup(t("enroll.transactionId"), error = if (errTxn) t("enroll.transactionIdError") else null) {
                EcTextField(txn, { txn = it }, placeholder = "e.g. 9G7H2K1LMN", isError = errTxn)
            }

            EcButton(
                text = if (sending) sendingText else t("common.submit"),
                enabled = !sending,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    attempted = true
                    if (!valid) {
                        onSent(invalidText)
                    } else {
                        sending = true
                        val draft = EnrollmentDraft(
                            name = name.trim(),
                            phone = phone.trim(),
                            email = email.trim(),
                            courses = courses.toList(),
                            mfsProvider = provider,
                            mfsTransactionId = txn.trim(),
                        )
                        scope.launch {
                            val ok = onSubmit(draft)
                            sending = false
                            if (ok) {
                                onSaveDraft(null)
                                onSent(thanksText)
                                onDismiss()
                            } else {
                                onSent(failedText)
                            }
                        }
                    }
                },
            )
        }
    }
}

// ---------- formatting helpers ----------

private fun fmtNum(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.2f", v)

private fun dateTime(ms: Long): String = SimpleDateFormat("d MMM yyyy · hh:mm a", Locale.US).format(Date(ms))

private fun time(ms: Long): String = SimpleDateFormat("hh:mm a", Locale.US).format(Date(ms))
