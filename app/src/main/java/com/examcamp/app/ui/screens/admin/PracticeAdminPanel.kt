package com.examcamp.app.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.examcamp.app.data.PracticeAdminEngine
import com.examcamp.app.data.PracticeCategory
import com.examcamp.app.data.PracticeQuestion
import com.examcamp.app.data.PracticeSubject
import com.examcamp.app.data.PracticeTopic
import com.examcamp.app.data.PracticeTree
import com.examcamp.app.data.parsePracticeQuestions
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

/**
 * Practice Admin (renderPracticeAdminPanel in app.js): categories, then subjects, then topics, then one topic's questions.
 * Each change is applied to a copy of the whole tree and saved with one PUT, as the website does.
 */
@Composable
internal fun PracticeAdminPanel(padmin: PracticeAdminEngine, toast: (String) -> Unit) {
    val c = Ec.c
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    var tree by remember { mutableStateOf<List<PracticeCategory>?>(null) }
    var catId by rememberSaveable { mutableStateOf<String?>(null) }
    var subId by rememberSaveable { mutableStateOf<String?>(null) }
    var topicId by rememberSaveable { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<PEditor?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    LaunchedEffect(Unit) {
        tree = try {
            padmin.loadTree()
        } catch (e: Exception) {
            toast(e.message ?: "Could not load the practice tree.")
            emptyList()
        }
    }

    val current = tree
    if (current == null) {
        Text(I18n.get(lang, "admin.practice.loading"), style = Ec.t.small, color = c.textSecondary)
        return
    }

    /** Saves the tree; on failure the screen goes back to the previous tree. */
    fun commit(next: List<PracticeCategory>, okText: String) {
        val previous = tree
        tree = next
        scope.launch {
            val err = padmin.saveTree(next)
            if (err != null) {
                tree = previous
                toast(err)
            } else {
                toast(okText)
            }
        }
    }

    val cat = current.firstOrNull { it.id == catId }
    val sub = cat?.subjects?.firstOrNull { it.id == subId }
    val topic = sub?.topics?.firstOrNull { it.id == topicId }

    // ---- navigation bar ----
    if (catId != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextAction(I18n.get(lang, "admin.practice.back"), c.primary) {
                when {
                    topicId != null -> topicId = null
                    subId != null -> subId = null
                    else -> catId = null
                }
            }
            Text(
                listOfNotNull(cat?.name, sub?.name, topic?.name).joinToString(" › "),
                style = Ec.t.smallStrong,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }

    when {
        // ---- level 1: categories ----
        catId == null -> {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(I18n.get(lang, "admin.practice.categories"), style = Ec.t.h4, color = c.textPrimary, modifier = Modifier.weight(1f))
                EcButton(I18n.get(lang, "admin.practice.addCategory"), onClick = { editor = PEditor.NewCategory }, small = true)
            }
            if (current.isEmpty()) Text(I18n.get(lang, "admin.empty"), style = Ec.t.small, color = c.textSecondary)
            current.forEach { cc ->
                EcCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${cc.icon.takeIf { !it.startsWith("data:") }.orEmpty()} ${cc.name}".trim(),
                            style = Ec.t.bodyStrong,
                            color = c.textPrimary,
                            modifier = Modifier.weight(1f).clickable(role = Role.Button) { catId = cc.id },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("${cc.subjects.size}", style = Ec.t.caption, color = c.textMuted)
                    }
                    Row {
                        TextAction(I18n.get(lang, "common.edit"), c.primary) { editor = PEditor.EditCategory(cc) }
                        TextAction(I18n.get(lang, "common.delete"), c.danger) {
                            confirm = I18n.get(lang, "admin.practice.confirmDeleteCategory") to {
                                commit(PracticeTree.deleteCategory(current, cc.id), I18n.get(lang, "admin.saved"))
                            }
                        }
                    }
                }
            }
        }

        // ---- level 2: subjects of the category ----
        subId == null -> {
            val subjects = cat?.subjects.orEmpty()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(I18n.get(lang, "admin.practice.subjects"), style = Ec.t.h4, color = c.textPrimary, modifier = Modifier.weight(1f))
                EcButton(I18n.get(lang, "admin.practice.addSubject"), onClick = { editor = PEditor.NewSubject }, small = true)
            }
            if (subjects.isEmpty()) Text(I18n.get(lang, "admin.empty"), style = Ec.t.small, color = c.textSecondary)
            subjects.forEach { s ->
                EcCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            s.name,
                            style = Ec.t.bodyStrong,
                            color = c.textPrimary,
                            modifier = Modifier.weight(1f).clickable(role = Role.Button) { subId = s.id },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("${s.topics.size}", style = Ec.t.caption, color = c.textMuted)
                    }
                    Row {
                        TextAction(I18n.get(lang, "admin.rename"), c.primary) { editor = PEditor.EditSubject(s) }
                        TextAction(I18n.get(lang, "common.delete"), c.danger) {
                            confirm = I18n.get(lang, "admin.practice.confirmDeleteSubject") to {
                                commit(PracticeTree.deleteSubject(current, catId!!, s.id), I18n.get(lang, "admin.saved"))
                            }
                        }
                    }
                }
            }
        }

        // ---- level 3: topics of the subject ----
        topicId == null -> {
            val topics = sub?.topics.orEmpty()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(I18n.get(lang, "admin.practice.topics"), style = Ec.t.h4, color = c.textPrimary, modifier = Modifier.weight(1f))
                EcButton(I18n.get(lang, "admin.practice.addTopic"), onClick = { editor = PEditor.NewTopic }, small = true)
            }
            if (topics.isEmpty()) Text(I18n.get(lang, "admin.practice.noTopic"), style = Ec.t.small, color = c.textSecondary)
            topics.forEach { tp ->
                EcCard(Modifier.fillMaxWidth()) {
                    Text(
                        "${tp.name} · ${tp.language}",
                        style = Ec.t.bodyStrong,
                        color = c.textPrimary,
                        modifier = Modifier.clickable(role = Role.Button) { topicId = tp.id },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row {
                        TextAction(I18n.get(lang, "common.edit"), c.primary) { editor = PEditor.EditTopic(tp) }
                        TextAction(I18n.get(lang, "common.delete"), c.danger) {
                            confirm = I18n.get(lang, "admin.practice.confirmDeleteTopic") to {
                                commit(PracticeTree.deleteTopic(current, catId!!, subId!!, tp.id), I18n.get(lang, "admin.saved"))
                            }
                        }
                    }
                }
            }
        }

        // ---- level 4: questions of one topic ----
        else -> {
            if (topic != null) {
                TopicQuestions(padmin, sub!!, topic, toast)
            }
        }
    }

    when (val ed = editor) {
        null -> Unit
        PEditor.NewCategory -> NameIconSheet(
            title = I18n.get(lang, "admin.practice.addCategory"),
            name = "",
            icon = "",
            showIcon = true,
            onDismiss = { editor = null },
            onSave = { name, icon ->
                commit(PracticeTree.addCategory(current, padmin.newId("pcat"), name, icon), I18n.get(lang, "admin.saved"))
                editor = null
            },
        )
        is PEditor.EditCategory -> NameIconSheet(
            title = I18n.get(lang, "common.edit"),
            name = ed.category.name,
            icon = ed.category.icon,
            showIcon = true,
            onDismiss = { editor = null },
            onSave = { name, icon ->
                commit(PracticeTree.updateCategory(current, ed.category.id, name, icon), I18n.get(lang, "admin.saved"))
                editor = null
            },
        )
        PEditor.NewSubject -> NameIconSheet(
            title = I18n.get(lang, "admin.practice.addSubject"),
            name = "",
            icon = "",
            showIcon = false,
            onDismiss = { editor = null },
            onSave = { name, _ ->
                commit(PracticeTree.addSubject(current, catId!!, padmin.newId("psub"), name), I18n.get(lang, "admin.saved"))
                editor = null
            },
        )
        is PEditor.EditSubject -> NameIconSheet(
            title = I18n.get(lang, "admin.rename"),
            name = ed.subject.name,
            icon = "",
            showIcon = false,
            onDismiss = { editor = null },
            onSave = { name, _ ->
                commit(PracticeTree.updateSubject(current, catId!!, ed.subject.id, name), I18n.get(lang, "admin.saved"))
                editor = null
            },
        )
        PEditor.NewTopic -> TopicSheet(
            title = I18n.get(lang, "admin.practice.addTopic"),
            name = "",
            language = "en",
            onDismiss = { editor = null },
            onSave = { name, language ->
                commit(PracticeTree.addTopic(current, catId!!, subId!!, padmin.newId("ptop"), name, language), I18n.get(lang, "admin.saved"))
                editor = null
            },
        )
        is PEditor.EditTopic -> TopicSheet(
            title = I18n.get(lang, "common.edit"),
            name = ed.topic.name,
            language = ed.topic.language,
            onDismiss = { editor = null },
            onSave = { name, language ->
                commit(PracticeTree.updateTopic(current, catId!!, subId!!, ed.topic.id, name, language), I18n.get(lang, "admin.saved"))
                editor = null
            },
        )
    }

    confirm?.let { (msg, action) ->
        ConfirmDialog(msg, onConfirm = { action(); confirm = null }, onDismiss = { confirm = null })
    }
}

private sealed interface PEditor {
    data object NewCategory : PEditor
    data class EditCategory(val category: PracticeCategory) : PEditor
    data object NewSubject : PEditor
    data class EditSubject(val subject: PracticeSubject) : PEditor
    data object NewTopic : PEditor
    data class EditTopic(val topic: PracticeTopic) : PEditor
}

/** Question editor for one topic: shows the saved count, replaces the bank from pasted JSON, or deletes it all. */
@Composable
private fun TopicQuestions(padmin: PracticeAdminEngine, subject: PracticeSubject, topic: PracticeTopic, toast: (String) -> Unit) {
    val c = Ec.c
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    var bank by remember(topic.id) { mutableStateOf<List<PracticeQuestion>?>(null) }
    var raw by remember(topic.id) { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(topic.id) {
        bank = padmin.loadQuestions(subject.id, topic.id)
    }

    val loaded = bank
    Text(
        if (loaded == null) I18n.get(lang, "admin.practice.loading")
        else I18n.get(lang, "admin.practice.count").format(loaded.size),
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
            scope.launch {
                val parsed = try {
                    parsePracticeQuestions(raw, loaded.orEmpty())
                } catch (e: IllegalArgumentException) {
                    toast("${I18n.get(lang, "admin.questions.invalid")} ${e.message}")
                    return@launch
                }
                val err = padmin.saveQuestions(subject.id, topic.id, topic.language, parsed)
                if (err == null) {
                    bank = parsed
                    toast(I18n.get(lang, "admin.questions.saved"))
                } else {
                    toast(err)
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    TextAction(I18n.get(lang, "admin.practice.clearAll"), c.danger) { confirmClear = true }
    if (confirmClear) {
        ConfirmDialog(
            message = I18n.get(lang, "admin.practice.confirmClear"),
            onConfirm = {
                confirmClear = false
                scope.launch {
                    val err = padmin.deleteQuestions(subject.id, topic.id)
                    if (err == null) bank = emptyList()
                    toast(err ?: I18n.get(lang, "admin.saved"))
                }
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun NameIconSheet(
    title: String,
    name: String,
    icon: String,
    showIcon: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    val c = Ec.c
    val lang = LocalLang.current
    var n by remember { mutableStateOf(name) }
    var i by remember { mutableStateOf(icon) }
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
            Text(title, style = Ec.t.h3, color = c.textPrimary)
            EcFormGroup(I18n.get(lang, "admin.subjectName")) { EcTextField(n, { n = it }) }
            if (showIcon) {
                EcFormGroup(I18n.get(lang, "admin.practice.icon")) { EcTextField(i, { i = it }, placeholder = "📚") }
            }
            EcButton(
                I18n.get(lang, "common.save"),
                onClick = { if (n.isNotBlank()) onSave(n.trim(), i.trim()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TopicSheet(
    title: String,
    name: String,
    language: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    val c = Ec.c
    val lang = LocalLang.current
    var n by remember { mutableStateOf(name) }
    var l by remember { mutableStateOf(language) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.s4).padding(bottom = Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            Text(title, style = Ec.t.h3, color = c.textPrimary)
            EcFormGroup(I18n.get(lang, "admin.topic")) { EcTextField(n, { n = it }) }
            EcFormGroup(I18n.get(lang, "admin.language")) {
                EcDropdown(
                    options = listOf("en" to I18n.get(lang, "common.langEnglish"), "bn" to I18n.get(lang, "common.langBengali")),
                    selected = l,
                    onSelect = { l = it },
                )
            }
            EcButton(
                I18n.get(lang, "common.save"),
                onClick = { if (n.isNotBlank()) onSave(n.trim(), l) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
