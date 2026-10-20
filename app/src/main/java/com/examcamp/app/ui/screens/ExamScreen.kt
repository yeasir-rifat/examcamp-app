package com.examcamp.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.examcamp.app.R
import com.examcamp.app.data.Question
import com.examcamp.app.data.Session
import com.examcamp.app.i18n.t
import com.examcamp.app.i18n.tf
import com.examcamp.app.ui.components.BtnStyle
import com.examcamp.app.ui.components.EcButton
import com.examcamp.app.ui.components.EcIcon
import com.examcamp.app.ui.math.MathText
import com.examcamp.app.ui.theme.Ec
import com.examcamp.app.ui.theme.EcIcons
import com.examcamp.app.ui.theme.Radius
import com.examcamp.app.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

// ---------- shared exam helpers (formatDuration, option letters, Bengali numerals in app.js) ----------

internal fun formatDuration(ms: Long): String {
    val totalSeconds = Math.max(0L, Math.round(ms / 1000.0))
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

private val BN_DIGITS = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
internal fun toBengaliDigits(text: String): String = text.map { if (it in '0'..'9') BN_DIGITS[it - '0'] else it }.joinToString("")

/** "bn" and "en-bn" exams use ক/খ/গ/ঘ markers and Bengali numerals (usesLocalizedLabels). */
internal fun usesLocalizedLabels(language: String) = language == "bn" || language == "en-bn"
internal fun optionLetters(localized: Boolean) = if (localized) listOf("ক", "খ", "গ", "ঘ") else listOf("A", "B", "C", "D")
internal fun formatQuestionNumber(index: Int, localized: Boolean): String {
    val padded = (index + 1).toString().padStart(2, '0')
    return (if (localized) toBengaliDigits(padded) else padded) + "."
}

/** Question, option and explanation text is always Cambria / Noto Serif Bengali on the website; serif is the closest on Android. */
@Composable
internal fun contentStyle(size: Float, bold: Boolean = false): TextStyle = Ec.t.body.copy(
    fontFamily = FontFamily.Serif,
    fontSize = size.sp,
    lineHeight = (size * 1.45f).sp,
    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
)

// ---------- screen ----------

@Composable
fun ExamScreen(
    session: Session,
    onAnswer: (String, Int) -> Unit,
    onMark: (String) -> Unit,
    onGoTo: (Int) -> Unit,
    onSubmit: () -> Unit,
    onTimeUp: () -> Unit,
    onBack: () -> Unit,
) {
    val c = Ec.c
    val exam = session.exam
    val localized = usesLocalizedLabels(exam.language)
    val listState = rememberLazyListState()
    var showSubmit by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    // Open at the question the person was last on, then keep "current question" in step with
    // scrolling (the website does the same with an IntersectionObserver). Order matters: the
    // initial scroll must finish before we start listening, or index 0 would overwrite the saved position.
    LaunchedEffect(listState) {
        listState.scrollToItem(session.currentQuestion.coerceIn(0, (exam.questions.size - 1).coerceAtLeast(0)))
        snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { onGoTo(it) }
    }

    // Timer: counted from startedAt, so it keeps running correctly while the app is in the background.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val total = exam.limitMs
    val remaining = (total - (now - session.startedAt)).coerceIn(0, total)
    LaunchedEffect(remaining <= 0L) { if (remaining <= 0L) onTimeUp() }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        ExamHeader(
            subject = exam.subject.ifBlank { "Exam" },
            topic = exam.topic,
            remaining = remaining,
            onSubmit = { showSubmit = true },
        )
        NavStrip(session, onJump = { i ->
            onGoTo(i)
        }, listState = listState)

        Text(
            tf("exam.progress", session.currentQuestion + 1, exam.questions.size),
            style = Ec.t.small, color = c.textSecondary,
            modifier = Modifier.padding(horizontal = Space.s4).padding(top = Space.s3, bottom = Space.s2),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Space.s4, end = Space.s4, bottom = Space.s6),
            verticalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            itemsIndexed(exam.questions, key = { _, q -> q.id }) { index, q ->
                QuestionCard(
                    index = index,
                    q = q,
                    localized = localized,
                    selected = session.answers[q.id],
                    marked = q.id in session.markedForReview,
                    current = index == session.currentQuestion,
                    onSelect = { onAnswer(q.id, it) },
                    onMark = { onMark(q.id) },
                )
            }
        }
    }

    if (showSubmit) {
        SubmitDialog(
            answered = session.answers.size,
            unanswered = exam.questions.size - session.answers.size,
            marked = session.markedForReview.size,
            onContinue = { showSubmit = false },
            onConfirm = { showSubmit = false; onSubmit() },
        )
    }
}

@Composable
private fun ExamHeader(subject: String, topic: String, remaining: Long, onSubmit: () -> Unit) {
    val c = Ec.c
    Column(Modifier.fillMaxWidth().background(c.surface).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = Space.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            Image(
                painter = painterResource(R.drawable.brand_icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Fit,
            )
            Column(Modifier.weight(1f)) {
                Text(subject, style = Ec.t.smallStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (topic.isNotBlank()) {
                    Text(topic, style = Ec.t.caption.copy(fontSize = 11.sp), color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val submitLabel = t("exam.submitAria")
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(Radius.md)).background(c.primary)
                    .clickable(role = Role.Button, onClickLabel = submitLabel, onClick = onSubmit),
                contentAlignment = Alignment.Center,
            ) { EcIcon(EcIcons.Check, c.textOnPrimary, 20.dp) }
            TimerPill(remaining)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

@Composable
private fun TimerPill(remaining: Long) {
    val c = Ec.c
    val (bg, border, fg) = when {
        remaining <= 60_000L -> Triple(c.dangerLight, c.dangerBorder, c.danger)
        remaining <= 5 * 60_000L -> Triple(c.warningLight, c.warningBorder, c.warning)
        else -> Triple(c.surfaceAlt, c.border, c.textPrimary)
    }
    val shape = RoundedCornerShape(Radius.md)
    Box(
        Modifier.height(40.dp).clip(shape).background(bg).border(1.dp, border, shape).padding(horizontal = Space.s3),
        contentAlignment = Alignment.Center,
    ) {
        Text(formatDuration(remaining), style = Ec.t.bodyStrong, color = fg, maxLines = 1)
    }
}

/** The horizontal question-number strip pinned under the header (mobile-nav-strip). */
@Composable
private fun NavStrip(session: Session, onJump: (Int) -> Unit, listState: androidx.compose.foundation.lazy.LazyListState) {
    val c = Ec.c
    val stripState = rememberLazyListState()
    val current = session.currentQuestion
    // Follow the current question so its number is always visible.
    LaunchedEffect(current) { stripState.animateScrollToItem((current - 2).coerceAtLeast(0)) }
    var jump by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(jump) {
        jump?.let { listState.animateScrollToItem(it) }
        jump = null
    }
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        LazyRow(
            state = stripState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.s4, vertical = Space.s3),
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            itemsIndexed(session.exam.questions, key = { _, q -> q.id }) { index, q ->
                NavButton(
                    label = (index + 1).toString().padStart(2, '0'),
                    answered = session.answers[q.id] != null,
                    marked = q.id in session.markedForReview,
                    current = index == current,
                    onClick = { onJump(index); jump = index },
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

@Composable
private fun NavButton(label: String, answered: Boolean, marked: Boolean, current: Boolean, onClick: () -> Unit) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.sm)
    val bg = if (answered) c.successLight else c.surface
    val borderColor = when {
        current -> c.primary
        answered -> c.successBorder
        else -> c.borderStrong
    }
    Box(
        Modifier
            .size(40.dp)
            .clip(shape)
            .background(bg)
            .border(if (current) 1.5.dp else 1.dp, borderColor, shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Ec.t.smallStrong, color = if (answered) c.success else c.textPrimary)
        if (marked) {
            Box(Modifier.align(Alignment.TopEnd).padding(2.dp).size(6.dp).background(c.warning, CircleShape))
        }
    }
}

@Composable
private fun QuestionCard(
    index: Int,
    q: Question,
    localized: Boolean,
    selected: Int?,
    marked: Boolean,
    current: Boolean,
    onSelect: (Int) -> Unit,
    onMark: () -> Unit,
) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.lg)
    val letters = optionLetters(localized)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, if (current) c.primaryBorder else c.border, shape)
            .padding(Space.s4),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s3), verticalAlignment = Alignment.Top) {
            MathText(
                text = q.question,
                style = contentStyle(16f, bold = true),
                color = c.textPrimary,
                modifier = Modifier.weight(1f),
                prefix = formatQuestionNumber(index, localized),
                prefixColor = c.primary,
            )
            val markShape = RoundedCornerShape(Radius.md)
            Box(
                Modifier
                    .size(34.dp)
                    .clip(markShape)
                    .background(if (marked) c.warningLight else c.surface)
                    .border(1.dp, if (marked) c.warningBorder else c.border, markShape)
                    .clickable(role = Role.Button, onClickLabel = if (marked) t("exam.unmarkReview") else t("exam.markReview"), onClick = onMark),
                contentAlignment = Alignment.Center,
            ) { EcIcon(EcIcons.Bookmark, if (marked) c.warning else c.textSecondary, 17.dp) }
        }
        Box(Modifier.height(Space.s3))
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            q.options.forEachIndexed { idx, text ->
                OptionRow(letters.getOrElse(idx) { "" }, text, selected == idx) { onSelect(idx) }
            }
        }
    }
}

@Composable
private fun OptionRow(letter: String, text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.sm)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 38.dp)
            .clip(shape)
            .background(if (selected) c.primaryWash else Color.Transparent)
            .border(1.dp, if (selected) c.primary else c.border, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.s3, vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .background(if (selected) c.primary else Color.Transparent, CircleShape)
                .border(1.dp, if (selected) c.primary else c.borderStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(letter, style = Ec.t.caption.copy(fontWeight = FontWeight.SemiBold), color = if (selected) Color.White else c.textPrimary)
        }
        MathText(text, contentStyle(14f), c.textPrimary, Modifier.weight(1f))
    }
}

@Composable
private fun SubmitDialog(answered: Int, unanswered: Int, marked: Int, onContinue: () -> Unit, onConfirm: () -> Unit) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.lg)
    Dialog(onDismissRequest = onContinue) {
        Column(
            Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.border, shape).padding(Space.s5),
        ) {
            Text(t("exam.submitModal.title"), style = Ec.t.h4, color = c.textPrimary)
            Box(Modifier.height(Space.s3))
            SummaryRow(t("common.answered"), answered)
            SummaryRow(t("common.unanswered"), unanswered)
            SummaryRow(t("exam.submitModal.marked"), marked)
            Text(
                t("exam.submitModal.warning"),
                style = Ec.t.small, color = c.textSecondary,
                modifier = Modifier.padding(top = Space.s4),
            )
            Box(Modifier.height(Space.s5))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                EcButton(t("exam.submitModal.continue"), onContinue, Modifier.weight(1f), style = BtnStyle.Secondary)
                EcButton(t("exam.submitExam"), onConfirm, Modifier.weight(1f), style = BtnStyle.Danger)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: Int) {
    val c = Ec.c
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.s2),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = Ec.t.body, color = c.textPrimary)
        Text(value.toString(), style = Ec.t.bodyStrong, color = c.textPrimary, textAlign = TextAlign.End)
    }
}
