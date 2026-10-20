package com.examcamp.app.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.examcamp.app.data.AdminEngine
import com.examcamp.app.data.LiveExamEngine
import com.examcamp.app.data.LiveMember
import com.examcamp.app.data.parseQuestionBank
import com.examcamp.app.i18n.I18n
import com.examcamp.app.i18n.LocalLang
import com.examcamp.app.ui.components.EcButton
import com.examcamp.app.ui.components.EcCard
import com.examcamp.app.ui.components.EcDropdown
import com.examcamp.app.ui.components.EcFormGroup
import com.examcamp.app.ui.components.EcTextField
import com.examcamp.app.ui.theme.Ec
import com.examcamp.app.ui.theme.Radius
import com.examcamp.app.ui.theme.Space
import kotlinx.coroutines.launch

private val ROLES = listOf("subscriber", "assistant", "admin")

// ---------- Users (members table) ----------

@Composable
internal fun UsersTab(live: LiveExamEngine, admin: AdminEngine, toast: (String) -> Unit) {
    val c = Ec.c
    val lang = LocalLang.current
    val members by live.members.collectAsStateWithLifecycle()
    val subjects by live.subjects.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<LiveMember?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<LiveMember?>(null) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(I18n.get(lang, "admin.users.heading"), style = Ec.t.h4, color = c.textPrimary, modifier = Modifier.weight(1f))
        EcButton(I18n.get(lang, "admin.users.add"), onClick = { editing = null; sheetOpen = true }, small = true)
    }
    if (members.isEmpty()) Text(I18n.get(lang, "admin.empty"), style = Ec.t.small, color = c.textSecondary)
    members.forEach { m ->
        EcCard(Modifier.fillMaxWidth()) {
            Text(m.name.ifBlank { m.email }, style = Ec.t.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${m.email} · ${m.role}", style = Ec.t.caption, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                m.subjects.joinToString(", ").ifBlank { I18n.get(lang, "admin.users.noSubjects") },
                style = Ec.t.small,
                color = c.textSecondary,
            )
            Row {
                TextAction(I18n.get(lang, "common.edit"), c.primary) { editing = m; sheetOpen = true }
                TextAction(I18n.get(lang, "common.delete"), c.danger) { confirm = m }
            }
        }
    }

    if (sheetOpen) {
        MemberSheet(
            member = editing,
            subjects = subjects.map { it.name },
            onDismiss = { sheetOpen = false },
            onSave = { m ->
                scope.launch { toast(admin.saveMember(m) ?: I18n.get(lang, "admin.saved")) }
                sheetOpen = false
            },
        )
    }

    confirm?.let { m ->
        ConfirmDialog(
            message = I18n.get(lang, "admin.confirmDeleteMember"),
            onConfirm = {
                scope.launch { toast(admin.deleteMember(m.email) ?: I18n.get(lang, "admin.saved")) }
                confirm = null
            },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun MemberSheet(
    member: LiveMember?,
    subjects: List<String>,
    onDismiss: () -> Unit,
    onSave: (LiveMember) -> Unit,
) {
    val c = Ec.c
    val lang = LocalLang.current
    var email by remember { mutableStateOf(member?.email.orEmpty()) }
    var name by remember { mutableStateOf(member?.name.orEmpty()) }
    var role by remember { mutableStateOf(member?.role?.takeIf { it.isNotBlank() } ?: "subscriber") }
    var chosen by remember { mutableStateOf<Set<String>>(member?.subjects?.toSet().orEmpty()) }
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
            Text(if (member == null) I18n.get(lang, "admin.users.add") else I18n.get(lang, "common.edit"), style = Ec.t.h3, color = c.textPrimary)
            EcFormGroup(I18n.get(lang, "enroll.email")) {
                EcTextField(email, { email = it }, placeholder = "name@gmail.com", keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
            }
            EcFormGroup(I18n.get(lang, "enroll.name")) { EcTextField(name, { name = it }) }
            EcFormGroup(I18n.get(lang, "admin.users.role")) {
                EcDropdown(options = ROLES.map { it to it }, selected = role, onSelect = { role = it })
            }
            EcFormGroup(I18n.get(lang, "enroll.coursesLabel")) {
                Column {
                    if (subjects.isEmpty()) Text(I18n.get(lang, "admin.empty"), style = Ec.t.small, color = c.textSecondary)
                    subjects.forEach { s ->
                        val on = s in chosen
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Checkbox) { chosen = if (on) chosen - s else chosen + s }
                                .padding(vertical = Space.s1),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = c.primary))
                            Text(s, style = Ec.t.body, color = c.textPrimary)
                        }
                    }
                }
            }
            if (error != null) Text(error!!, style = Ec.t.caption, color = c.danger)
            EcButton(
                I18n.get(lang, "common.save"),
                onClick = {
                    val mail = email.trim().lowercase()
                    error = if (!Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(mail)) I18n.get(lang, "enroll.emailError") else null
                    if (error == null) {
                        onSave(LiveMember(mail, name.trim(), role, chosen.toList()))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------- Questions (question bank per live exam) ----------

@Composable
internal fun QuestionsTab(live: LiveExamEngine, admin: AdminEngine, toast: (String) -> Unit) {
    val c = Ec.c
    val lang = LocalLang.current
    val exams by live.exams.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val sorted = exams.sortedBy { it.startMs }
    var examId by rememberSaveable { mutableStateOf("") }
    val exam = sorted.firstOrNull { it.id == examId } ?: sorted.firstOrNull()
    var raw by rememberSaveable { mutableStateOf("") }

    if (sorted.isEmpty()) {
        Text(I18n.get(lang, "admin.questions.noExam"), style = Ec.t.small, color = c.textSecondary)
        return
    }
    EcFormGroup(I18n.get(lang, "admin.questions.exam")) {
        EcDropdown(
            options = sorted.map { it.id to "${it.subject} · ${it.topic}" },
            selected = exam?.id.orEmpty(),
            onSelect = { examId = it },
        )
    }
    Text(
        I18n.get(lang, "admin.questions.current").format(exam?.questionCount ?: 0),
        style = Ec.t.caption,
        color = c.textMuted,
    )
    EcFormGroup(I18n.get(lang, "admin.questions.json")) {
        EcTextField(raw, { raw = it }, placeholder = "[ { \"question\": \"...\", \"options\": [\"A\",\"B\",\"C\",\"D\"], \"correctAnswer\": 0, \"explanation\": \"...\" } ]", singleLine = false, height = 240.dp)
    }
    Text(I18n.get(lang, "admin.questions.hint"), style = Ec.t.caption, color = c.textMuted)
    EcButton(
        I18n.get(lang, "admin.questions.save"),
        onClick = {
            val target = exam ?: return@EcButton
            val parsed = try {
                parseQuestionBank(raw)
            } catch (e: IllegalArgumentException) {
                toast("${I18n.get(lang, "admin.questions.invalid")} ${e.message}")
                null
            }
            if (parsed != null) {
                scope.launch { toast(admin.saveQuestions(target, parsed) ?: I18n.get(lang, "admin.questions.saved")) }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
