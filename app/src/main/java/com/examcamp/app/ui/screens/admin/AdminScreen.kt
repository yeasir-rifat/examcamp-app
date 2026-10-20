package com.examcamp.app.ui.screens.admin

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.examcamp.app.data.AdminEngine
import com.examcamp.app.data.EnrollmentRequest
import com.examcamp.app.data.LiveExam
import com.examcamp.app.data.LiveExamEngine
import com.examcamp.app.data.LiveSubject
import com.examcamp.app.data.PracticeAdminEngine
import com.examcamp.app.i18n.I18n
import com.examcamp.app.i18n.LocalLang
import com.examcamp.app.ui.components.EcButton
import com.examcamp.app.ui.components.EcCard
import com.examcamp.app.ui.components.EcDropdown
import com.examcamp.app.ui.components.EcFormGroup
import com.examcamp.app.ui.components.EcIcon
import com.examcamp.app.ui.components.EcTextField
import com.examcamp.app.ui.screens.SubpageHeader
import com.examcamp.app.ui.theme.Ec
import com.examcamp.app.ui.theme.EcIcons
import com.examcamp.app.ui.theme.Radius
import com.examcamp.app.ui.theme.Space
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Admin page (#view-admin): Courses, Questions, Results, Users and Enrollments. Opened only for admins. */
@Composable
fun AdminScreen(
    live: LiveExamEngine,
    admin: AdminEngine,
    padmin: PracticeAdminEngine,
    isPracticeAdmin: Boolean,
    onHome: () -> Unit,
    onBack: () -> Unit,
) {
    val c = Ec.c
    val lang = LocalLang.current
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) }
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    val tabKeys = listOf("admin.tab.courses", "admin.tab.questions", "admin.tab.results", "admin.tab.users", "admin.tab.enrollments") +
        (if (isPracticeAdmin) listOf("admin.tab.practice") else emptyList())

    LaunchedEffect(Unit) {
        live.refresh()
        admin.reloadPending()
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        SubpageHeader(I18n.get(lang, "admin.title"), onHome, onBack)
        AdminTabs(tabKeys, tab) { tab = it }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            when (tab) {
                0 -> CoursesTab(live, admin, toast)
                1 -> QuestionsTab(live, admin, toast)
                2 -> ResultsTab(live, admin, toast)
                3 -> UsersTab(live, admin, toast)
                4 -> EnrollmentsTab(live, admin, toast)
                else -> PracticeAdminPanel(padmin, toast)
            }
        }
    }
}

// ---------- shared pieces ----------

@Composable
internal fun AdminTabs(keys: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Ec.c
    val lang = LocalLang.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            keys.forEachIndexed { i, key ->
                val on = i == selected
                Column(
                    Modifier
                        .clickable(role = Role.Tab) { onSelect(i) }
                        .padding(horizontal = Space.s3),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                        Text(I18n.get(lang, key), style = Ec.t.smallStrong, color = if (on) c.primary else c.textSecondary, maxLines = 1)
                    }
                    Box(Modifier.fillMaxWidth().height(2.dp).background(if (on) c.primary else Color.Transparent))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

@Composable
internal fun TextAction(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        style = Ec.t.smallStrong,
        color = color,
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.s2, vertical = Space.s1),
    )
}

@Composable
internal fun ConfirmDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val lang = LocalLang.current
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message, style = Ec.t.body, color = Ec.c.textPrimary) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(I18n.get(lang, "common.delete"), color = Ec.c.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(I18n.get(lang, "common.cancel"), color = Ec.c.textSecondary) } },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(com.examcamp.app.ui.theme.Radius.lg), // .modal: 8px
        containerColor = Ec.c.surface,
        tonalElevation = androidx.compose.ui.unit.Dp.Hairline,
    )
}

internal val examDateFormat: SimpleDateFormat
    get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

internal fun formatExamDate(ms: Long): String =
    SimpleDateFormat("d MMM yyyy, hh:mm a", Locale.US).format(Date(ms))

// ---------- Courses ----------

private sealed interface Editor {
    data object NewSubject : Editor
    data class RenameSubject(val subject: LiveSubject) : Editor
    data class ExamEdit(val exam: LiveExam?) : Editor
}

@Composable
private fun CoursesTab(live: LiveExamEngine, admin: AdminEngine, toast: (String) -> Unit) {
    val c = Ec.c
    val lang = LocalLang.current
    val subjects by live.subjects.collectAsStateWithLifecycle()
    val exams by live.exams.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editor by remember { mutableStateOf<Editor?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val savedText = I18n.get(lang, "admin.saved")

    fun done(err: String?) = toast(err ?: savedText)

    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(I18n.get(lang, "admin.courses.subjects"), style = Ec.t.h4, color = c.textPrimary, modifier = Modifier.weight(1f))
            EcButton(I18n.get(lang, "admin.addSubject"), onClick = { editor = Editor.NewSubject }, small = true)
        }
        if (subjects.isEmpty()) Text(I18n.get(lang, "admin.empty"), style = Ec.t.small, color = c.textSecondary)
        subjects.forEach { s ->
            EcCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, style = Ec.t.bodyStrong, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextAction(I18n.get(lang, "admin.rename"), c.primary) { editor = Editor.RenameSubject(s) }
                    TextAction(I18n.get(lang, "common.delete"), c.danger) {
                        confirm = I18n.get(lang, "admin.confirmDeleteSubject") to {
                            scope.launch { done(admin.deleteSubject(s.id)) }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(I18n.get(lang, "admin.courses.exams"), style = Ec.t.h4, color = c.textPrimary, modifier = Modifier.weight(1f))
            EcButton(I18n.get(lang, "admin.addExam"), onClick = { editor = Editor.ExamEdit(null) }, small = true)
        }
        if (exams.isEmpty()) Text(I18n.get(lang, "admin.empty"), style = Ec.t.small, color = c.textSecondary)
        exams.sortedBy { it.startMs }.forEach { e ->
            EcCard(Modifier.fillMaxWidth()) {
                Text("${e.subject} · ${e.topic}", style = Ec.t.bodyStrong, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${formatExamDate(e.startMs)} · ${e.durationMinutes} min · ${e.questionCount} Q · ${e.status}",
                    style = Ec.t.caption,
                    color = c.textMuted,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(I18n.get(lang, "admin.published"), style = Ec.t.small, color = c.textSecondary, modifier = Modifier.weight(1f))
                    Switch(
                        checked = e.published,
                        onCheckedChange = { on -> scope.launch { done(admin.setPublished(e, on)) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
                    )
                }
                Row {
                    TextAction(I18n.get(lang, "common.edit"), c.primary) { editor = Editor.ExamEdit(e) }
                    TextAction(I18n.get(lang, "common.delete"), c.danger) {
                        confirm = I18n.get(lang, "admin.confirmDeleteExam") to { scope.launch { done(admin.deleteExam(e.id)) } }
                    }
                }
            }
        }
    }

    when (val ed = editor) {
        null -> Unit
        Editor.NewSubject -> NameSheet(
            title = I18n.get(lang, "admin.addSubject"),
            initial = "",
            onDismiss = { editor = null },
            onSave = { name ->
                scope.launch { done(admin.addSubject(name)) }
                editor = null
            },
        )
        is Editor.RenameSubject -> NameSheet(
            title = I18n.get(lang, "admin.rename"),
            initial = ed.subject.name,
            onDismiss = { editor = null },
            onSave = { name ->
                scope.launch { done(admin.renameSubject(ed.subject.id, name)) }
                editor = null
            },
        )
        is Editor.ExamEdit -> ExamSheet(
            exam = ed.exam,
            subjects = subjects,
            onDismiss = { editor = null },
            onSave = { exam, errorText ->
                if (exam == null) {
                    toast(errorText ?: "")
                } else {
                    scope.launch { done(admin.saveExam(exam, ed.exam?.questionCount ?: 0)) }
                    editor = null
                }
            },
        )
    }

    confirm?.let { (msg, action) ->
        ConfirmDialog(msg, onConfirm = { action(); confirm = null }, onDismiss = { confirm = null })
    }
}

@Composable
private fun NameSheet(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = Ec.c
    val lang = LocalLang.current
    var name by remember { mutableStateOf(initial) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.s4).padding(bottom = Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
            Text(title, style = Ec.t.h3, color = c.textPrimary)
            EcFormGroup(I18n.get(lang, "admin.subjectName")) {
                EcTextField(name, { name = it })
            }
            EcButton(I18n.get(lang, "common.save"), onClick = { if (name.isNotBlank()) onSave(name.trim()) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Exam editor. Start time is typed as local "yyyy-MM-dd HH:mm". Publish is changed from the list, not here. */
@Composable
private fun ExamSheet(
    exam: LiveExam?,
    subjects: List<LiveSubject>,
    onDismiss: () -> Unit,
    onSave: (LiveExam?, String?) -> Unit,
) {
    val c = Ec.c
    val lang = LocalLang.current
    val defaultStart = examDateFormat.format(Date(System.currentTimeMillis() + 24L * 3_600_000L))
    var subject by remember { mutableStateOf(exam?.subject ?: subjects.firstOrNull()?.name.orEmpty()) }
    var topic by remember { mutableStateOf(exam?.topic.orEmpty()) }
    var language by remember { mutableStateOf(exam?.language ?: "en") }
    var start by remember { mutableStateOf(exam?.let { formatInput(it.startMs) } ?: defaultStart) }
    var duration by remember { mutableStateOf((exam?.durationMinutes ?: 60).toString()) }
    var marks by remember { mutableStateOf((exam?.marksPerQuestion ?: 1.0).toString()) }
    var negative by remember { mutableStateOf((exam?.negativeMarking ?: 0.0).toString()) }
    var status by remember { mutableStateOf(exam?.status ?: "scheduled") }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Space.s4).padding(bottom = Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            Text(if (exam == null) I18n.get(lang, "admin.addExam") else I18n.get(lang, "common.edit"), style = Ec.t.h3, color = c.textPrimary)
            EcFormGroup(I18n.get(lang, "admin.subject")) {
                EcDropdown(
                    options = subjects.map { it.name to it.name },
                    selected = subject,
                    onSelect = { subject = it },
                    placeholder = I18n.get(lang, "admin.subject"),
                )
            }
            EcFormGroup(I18n.get(lang, "admin.topic")) { EcTextField(topic, { topic = it }) }
            EcFormGroup(I18n.get(lang, "admin.language")) {
                EcDropdown(
                    options = listOf("en" to I18n.get(lang, "common.langEnglish"), "bn" to I18n.get(lang, "common.langBengali"), "en-bn" to I18n.get(lang, "common.langMixed")),
                    selected = language,
                    onSelect = { language = it },
                )
            }
            EcFormGroup(I18n.get(lang, "admin.start")) { EcTextField(start, { start = it }, placeholder = "2026-10-25 09:00") }
            EcFormGroup(I18n.get(lang, "admin.duration")) { EcTextField(duration, { duration = it }, keyboardType = androidx.compose.ui.text.input.KeyboardType.Number) }
            EcFormGroup(I18n.get(lang, "admin.marks")) { EcTextField(marks, { marks = it }, keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal) }
            EcFormGroup(I18n.get(lang, "admin.negative")) { EcTextField(negative, { negative = it }, keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal) }
            EcFormGroup(I18n.get(lang, "admin.status")) {
                EcDropdown(
                    options = listOf("scheduled" to "scheduled", "live" to "live", "ended" to "ended"),
                    selected = status,
                    onSelect = { status = it },
                )
            }
            if (error != null) Text(error!!, style = Ec.t.caption, color = c.danger)
            EcButton(
                I18n.get(lang, "common.save"),
                onClick = {
                    val startMs = parseInput(start)
                    val dur = duration.trim().toIntOrNull()
                    val m = marks.trim().toDoubleOrNull()
                    val n = negative.trim().toDoubleOrNull()
                    error = when {
                        subject.isBlank() -> I18n.get(lang, "admin.err.subject")
                        topic.isBlank() -> I18n.get(lang, "admin.err.topic")
                        startMs == null -> I18n.get(lang, "admin.err.start")
                        dur == null || dur <= 0 -> I18n.get(lang, "admin.err.duration")
                        m == null || m <= 0 -> I18n.get(lang, "admin.err.marks")
                        n == null || n < 0 -> I18n.get(lang, "admin.err.negative")
                        else -> null
                    }
                    if (error == null && startMs != null && dur != null && m != null && n != null) {
                        onSave(
                            LiveExam(
                                id = exam?.id ?: "exam-${System.currentTimeMillis()}",
                                subject = subject,
                                topic = topic.trim(),
                                language = language,
                                startMs = startMs,
                                durationMinutes = dur,
                                marksPerQuestion = m,
                                negativeMarking = n,
                                status = status,
                                published = exam?.published ?: false,
                                questionCount = exam?.questionCount ?: 0,
                            ),
                            null,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatInput(ms: Long): String = examDateFormat.format(Date(ms))

private fun parseInput(text: String): Long? = try {
    examDateFormat.apply { isLenient = false }.parse(text.trim())?.time
} catch (e: Exception) {
    null
}

// ---------- Results ----------

@Composable
private fun ResultsTab(live: LiveExamEngine, admin: AdminEngine, toast: (String) -> Unit) {
    val c = Ec.c
    val lang = LocalLang.current
    val exams by live.exams.collectAsStateWithLifecycle()
    val subs by live.allSubmissions.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var openExam by rememberSaveable { mutableStateOf<String?>(null) }
    val now = System.currentTimeMillis()
    val ended = exams.filter { now >= it.endMs }.sortedBy { it.endMs }
    val bySubject = ended.groupBy { it.subject }

    if (ended.isEmpty()) {
        Text(I18n.get(lang, "admin.results.empty"), style = Ec.t.small, color = c.textSecondary)
        return
    }
    bySubject.forEach { (subject, list) ->
        val allOn = list.all { it.published }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(subject, style = Ec.t.h4, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            EcButton(
                if (allOn) I18n.get(lang, "admin.unpublishAll") else I18n.get(lang, "admin.publishAll"),
                onClick = { scope.launch { toast(admin.setSubjectPublished(list, !allOn) ?: I18n.get(lang, "admin.saved")) } },
                small = true,
            )
        }
        list.forEach { e ->
            val mine = subs.filter { it.examId == e.id }
            EcCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.topic, style = Ec.t.bodyStrong, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${formatExamDate(e.startMs)} · ${mine.size} ${I18n.get(lang, "admin.results.submissions")}", style = Ec.t.caption, color = c.textMuted)
                    }
                    Switch(
                        checked = e.published,
                        onCheckedChange = { on -> scope.launch { toast(admin.setPublished(e, on) ?: I18n.get(lang, "admin.saved")) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
                    )
                }
                TextAction(
                    if (openExam == e.id) I18n.get(lang, "admin.results.hide") else I18n.get(lang, "admin.results.show"),
                    c.primary,
                ) { openExam = if (openExam == e.id) null else e.id }
                if (openExam == e.id) {
                    if (mine.isEmpty()) {
                        Text(I18n.get(lang, "admin.results.none"), style = Ec.t.small, color = c.textSecondary)
                    } else {
                        mine.sortedByDescending { it.scorePercent }.forEach { s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = Space.s1), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.name.ifBlank { s.email }, style = Ec.t.body, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(s.email, style = Ec.t.caption, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text("${s.scorePercent}%", style = Ec.t.bodyStrong, color = c.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Enrollments ----------

@Composable
private fun EnrollmentsTab(live: LiveExamEngine, admin: AdminEngine, toast: (String) -> Unit) {
    val c = Ec.c
    val lang = LocalLang.current
    val pending by admin.pending.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    if (pending.isEmpty()) {
        Text(I18n.get(lang, "admin.enroll.empty"), style = Ec.t.small, color = c.textSecondary)
        return
    }
    pending.forEach { req -> EnrollmentCard(req) { ok ->
        scope.launch {
            toast(if (ok) admin.approve(req) ?: I18n.get(lang, "admin.enroll.approved") else admin.dismiss(req) ?: I18n.get(lang, "admin.enroll.dismissed"))
        }
    } }
}

@Composable
private fun EnrollmentCard(req: EnrollmentRequest, onDecide: (approve: Boolean) -> Unit) {
    val c = Ec.c
    val lang = LocalLang.current
    EcCard(Modifier.fillMaxWidth()) {
        Text(req.name, style = Ec.t.bodyStrong, color = c.textPrimary)
        Text("${req.phone} · ${req.email}", style = Ec.t.caption, color = c.textSecondary)
        Text(req.courses.joinToString(", "), style = Ec.t.small, color = c.textPrimary)
        Text("${req.mfsProvider} · ${req.txn}", style = Ec.t.caption, color = c.textMuted)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            EcButton(I18n.get(lang, "admin.enroll.approve"), onClick = { onDecide(true) }, small = true, modifier = Modifier.weight(1f))
            TextAction(I18n.get(lang, "admin.enroll.dismiss"), c.danger) { onDecide(false) }
        }
    }
}
