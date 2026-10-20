package com.examcamp.app.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.examcamp.app.data.Attachment
import com.examcamp.app.data.ExamConfig
import com.examcamp.app.data.ExamPrefs
import com.examcamp.app.i18n.t
import com.examcamp.app.ui.components.BtnStyle
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
import java.io.File

/** All AI Mode form fields. Lives above the Home tabs so switching tabs or leaving Home never loses what was typed. */
class AiFormState {
    var prompt by mutableStateOf("")
    var subject by mutableStateOf("")
    var topic by mutableStateOf("")
    var questions by mutableStateOf("50")
    var difficulty by mutableStateOf("medium")
    var language by mutableStateOf("en-bn")
    var time by mutableStateOf("60")
    var showErrors by mutableStateOf(false)
    val attachments: SnapshotStateList<Attachment> = mutableStateListOf()

    val promptInvalid get() = prompt.isBlank()
    val subjectInvalid get() = subject.isBlank()
    val topicInvalid get() = topic.isBlank()
    val questionsInvalid: Boolean
        get() {
            val n = questions.toIntOrNull()
            return n == null || n < 1 || n > 200
        }
    val isValid get() = !promptInvalid && !subjectInvalid && !topicInvalid && !questionsInvalid

    /** readAiModeFormConfig: negative marking is 0 unless the toggle in Settings is on. */
    fun toConfig(prefs: ExamPrefs) = ExamConfig(
        prompt = prompt.trim(),
        subject = subject.trim(),
        topic = topic.trim(),
        questionCount = questions.toIntOrNull() ?: 0,
        difficulty = difficulty,
        language = language,
        duration = time.toIntOrNull() ?: 60,
        negativeMarking = if (prefs.negativeMarkEnabled) prefs.negativeMarkValue else 0.0,
        attachmentCount = attachments.size,
    )
}

private const val MAX_BYTES = 15L * 1024 * 1024
private val ACCEPTED_EXT = Regex("""\.(png|jpe?g|webp|heic|heif|pdf|docx?|pptx?)$""", RegexOption.IGNORE_CASE)
private val PICKER_TYPES = arrayOf(
    "image/*",
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
)

private fun mimeFromName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    "heic" -> "image/heic"
    "heif" -> "image/heif"
    "pdf" -> "application/pdf"
    "doc" -> "application/msword"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "ppt" -> "application/vnd.ms-powerpoint"
    "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    else -> ""
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

private fun describe(context: Context, uri: Uri, fallbackName: String): Attachment {
    var name = fallbackName
    var size = 0L
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val si = c.getColumnIndex(OpenableColumns.SIZE)
            if (ni >= 0) c.getString(ni)?.let { name = it }
            if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
        }
    }
    val mime = (context.contentResolver.getType(uri) ?: "").ifBlank { mimeFromName(name) }
    return Attachment("att_${System.nanoTime()}", uri, name, size, mime)
}

/** The AI Mode panel (home-panel-ai in index.html). */
@Composable
fun AiModeCard(form: AiFormState, prefs: ExamPrefs, onGenerate: (ExamConfig, List<Attachment>) -> Unit) {
    val c = Ec.c
    val context = LocalContext.current
    val msgUnsupported = t("home.aiMode.attachmentUnsupported")
    val msgTooLarge = t("home.aiMode.attachmentTooLarge")
    val msgFill = t("home.aiMode.fillRequired")

    fun add(list: List<Attachment>) {
        list.forEach { att ->
            when {
                !ACCEPTED_EXT.containsMatchIn(att.name) && !att.mimeType.startsWith("image/") && att.mimeType != "application/pdf" ->
                    Toast.makeText(context, "\"${att.name}\" - $msgUnsupported.", Toast.LENGTH_SHORT).show()
                att.size > MAX_BYTES ->
                    Toast.makeText(context, "\"${att.name}\" - $msgTooLarge.", Toast.LENGTH_SHORT).show()
                else -> form.attachments.add(att)
            }
        }
    }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        add(uris.map { describe(context, it, "file") })
    }
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = captureUri
        captureUri = null
        if (ok && uri != null) add(listOf(describe(context, uri, "capture.jpg").let { if (it.mimeType.isBlank()) it.copy(mimeType = "image/jpeg") else it }))
    }
    fun capture() {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        captureUri = uri
        takePicture.launch(uri)
    }

    val shape = RoundedCornerShape(Radius.lg)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(c.primary.copy(alpha = if (c.isDark) 0.08f else 0.10f), c.surface)))
            .border(1.dp, c.primaryBorder, shape)
            .padding(Space.s4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            EcIcon(EcIcons.Sparkle, c.primary, 20.dp)
            Text(t("home.aiMode.title").uppercase(), style = Ec.t.h4.copy(letterSpacing = 0.02.em), color = c.textPrimary)
        }
        Box(Modifier.height(Space.s5))

        EcFormGroup(t("home.aiMode.promptLabel"), error = if (form.showErrors && form.promptInvalid) t("home.aiMode.promptError") else null) {
            EcTextField(
                value = form.prompt,
                onValueChange = { form.prompt = it },
                placeholder = t("home.aiMode.promptPlaceholder"),
                singleLine = false,
                height = 140.dp,
                tinted = true,
                isError = form.showErrors && form.promptInvalid,
            )
        }
        Box(Modifier.height(Space.s5))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
            OutlineAction(EcIcons.Camera, t("home.aiMode.capture"), Modifier.weight(1f)) { capture() }
            OutlineAction(EcIcons.Upload, t("home.aiMode.upload"), Modifier.weight(1f)) { pickFiles.launch(PICKER_TYPES) }
        }

        if (form.attachments.isNotEmpty()) {
            Box(Modifier.height(Space.s2))
            Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                form.attachments.toList().forEach { att ->
                    AttachmentRow(att) { form.attachments.remove(att) }
                }
                AddMoreButton { pickFiles.launch(PICKER_TYPES) }
            }
        }
        Box(Modifier.height(Space.s5))

        // Questions + Difficulty, Language + Time pair up two per row; Subject and Topic are full width.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
            EcFormGroup(
                t("home.aiMode.questionsLabel"),
                Modifier.weight(1f),
                error = if (form.showErrors && form.questionsInvalid) t("home.aiMode.questionsError") else null,
            ) {
                EcTextField(
                    value = form.questions,
                    onValueChange = { form.questions = it.filter(Char::isDigit).take(3) },
                    placeholder = t("home.aiMode.questionsPlaceholder"),
                    keyboardType = KeyboardType.Number,
                    tinted = true,
                    isError = form.showErrors && form.questionsInvalid,
                )
            }
            EcFormGroup(t("home.aiMode.difficultyLabel"), Modifier.weight(1f)) {
                EcDropdown(
                    options = listOf("easy" to t("common.easy"), "medium" to t("common.medium"), "hard" to t("common.hard")),
                    selected = form.difficulty, onSelect = { form.difficulty = it }, tinted = true,
                )
            }
        }
        Box(Modifier.height(Space.s4))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
            EcFormGroup(t("home.aiMode.languageLabel"), Modifier.weight(1f)) {
                EcDropdown(
                    options = listOf("en" to t("common.langEnglish"), "bn" to t("common.langBengali"), "en-bn" to t("common.langMixed")),
                    selected = form.language, onSelect = { form.language = it }, tinted = true,
                )
            }
            EcFormGroup(t("home.aiMode.timeLabel"), Modifier.weight(1f)) {
                EcDropdown(
                    options = listOf("10", "20", "30", "60", "90", "120").map { it to t("common.min$it") },
                    selected = form.time, onSelect = { form.time = it }, tinted = true,
                )
            }
        }
        Box(Modifier.height(Space.s4))
        EcFormGroup(t("home.aiMode.subjectLabel"), error = if (form.showErrors && form.subjectInvalid) t("home.aiMode.subjectError") else null) {
            EcTextField(
                value = form.subject, onValueChange = { form.subject = it },
                placeholder = t("home.aiMode.subjectPlaceholder"), tinted = true,
                isError = form.showErrors && form.subjectInvalid,
            )
        }
        Box(Modifier.height(Space.s4))
        EcFormGroup(t("home.aiMode.topicLabel"), error = if (form.showErrors && form.topicInvalid) t("home.aiMode.topicError") else null) {
            EcTextField(
                value = form.topic, onValueChange = { form.topic = it },
                placeholder = t("home.aiMode.topicPlaceholder"), tinted = true,
                isError = form.showErrors && form.topicInvalid,
            )
        }
        Box(Modifier.height(Space.s6))

        EcButton(
            text = t("home.aiMode.generate"),
            onClick = {
                form.showErrors = true
                if (!form.isValid) {
                    Toast.makeText(context, msgFill, Toast.LENGTH_SHORT).show()
                } else {
                    onGenerate(form.toConfig(prefs), form.attachments.toList())
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        )
    }
}

@Composable
private fun OutlineAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.sm)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .border(1.dp, c.primaryBorder, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.s4),
        horizontalArrangement = Arrangement.spacedBy(Space.s2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EcIcon(icon, c.primary, 18.dp)
        Text(label, style = Ec.t.button, color = c.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AttachmentRow(att: Attachment, onRemove: () -> Unit) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.md)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surfaceAlt)
            .border(1.dp, c.border, shape)
            .padding(horizontal = Space.s3, vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(Radius.sm)).background(c.primaryLight),
            contentAlignment = Alignment.Center,
        ) {
            if (att.kind == Attachment.Kind.IMAGE) {
                AsyncImage(model = att.uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(32.dp))
            } else {
                EcIcon(EcIcons.File, c.primary, 18.dp)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(att.name, style = Ec.t.small, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatFileSize(att.size), style = Ec.t.caption, color = c.textMuted)
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { EcIcon(EcIcons.Close, c.textMuted, 16.dp) }
    }
}

@Composable
private fun AddMoreButton(onClick: () -> Unit) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.md)
    val border = c.primaryBorder
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .drawBehind {
                drawRoundRect(
                    color = border,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.s3, vertical = Space.s2),
        horizontalArrangement = Arrangement.spacedBy(Space.s2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EcIcon(EcIcons.Plus, c.primary, 14.dp)
        Text(t("home.aiMode.addMore"), style = Ec.t.label, color = c.primary)
    }
}
