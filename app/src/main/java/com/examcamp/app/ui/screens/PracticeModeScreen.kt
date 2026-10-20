package com.examcamp.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examcamp.app.R
import com.examcamp.app.data.PracticeQuestion
import com.examcamp.app.data.PracticeRun
import com.examcamp.app.i18n.t
import com.examcamp.app.i18n.tf
import com.examcamp.app.ui.components.EcIcon
import com.examcamp.app.ui.components.EcIconButton
import com.examcamp.app.ui.math.MathText
import com.examcamp.app.ui.theme.Ec
import com.examcamp.app.ui.theme.EcIcons
import com.examcamp.app.ui.theme.Radius
import com.examcamp.app.ui.theme.Space
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private enum class Outcome { UNANSWERED, CORRECT, WRONG }

private fun outcomeOf(q: PracticeQuestion, selected: Int?): Outcome = when {
    selected == null -> Outcome.UNANSWERED
    selected == q.correctAnswer -> Outcome.CORRECT
    else -> Outcome.WRONG
}

/**
 * Practice Mode (#view-practice-mode): Exam-Mode look, but no timer and no Submit. A live right/wrong
 * counter instead; picking an option locks it and reveals correct/wrong plus the explanation.
 */
@Composable
fun PracticeModeScreen(
    run: PracticeRun,
    onAnswer: (String, Int) -> Unit,
    onSetCurrent: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val c = Ec.c
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // This topic's own language decides ক/খ/গ/ঘ and Bengali numerals, not the app language.
    val localized = usesLocalizedLabels(run.language)

    BackHandler(onBack = onBack)

    // Open where the student left off, then keep "current" in step with scrolling.
    LaunchedEffect(listState) {
        listState.scrollToItem(run.current.coerceIn(0, (run.questions.size - 1).coerceAtLeast(0)))
        snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { onSetCurrent(it) }
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        PracticeHeader(run.subjectName, run.topicName, run.right, run.wrong, onBack)
        PracticeNavStrip(run) { i ->
            onSetCurrent(i)
            scope.launch { listState.animateScrollToItem(i) }
        }
        Text(
            tf("exam.progress", run.current + 1, run.questions.size),
            style = Ec.t.small, color = c.textSecondary,
            modifier = Modifier.padding(horizontal = Space.s4).padding(top = Space.s3, bottom = Space.s2),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = Space.s4, end = Space.s4, bottom = Space.s6),
            verticalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            itemsIndexed(run.questions, key = { index, q -> "${q.id}#$index" }) { index, q ->
                PracticeQuestionCard(
                    index = index,
                    q = q,
                    localized = localized,
                    selected = run.answers[q.id],
                    current = index == run.current,
                    onSelect = { onAnswer(q.id, it) },
                )
            }
        }
    }
}

@Composable
private fun PracticeHeader(subject: String, topic: String, right: Int, wrong: Int, onBack: () -> Unit) {
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
            ScorePill(right, wrong)
            EcIconButton(EcIcons.ChevronLeft, t("practice.back"), onBack, size = 40.dp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

/** .pm-score-pill: green right count, red wrong count, hairline between. */
@Composable
private fun ScorePill(right: Int, wrong: Int) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.md)
    Row(
        Modifier.height(40.dp).clip(shape).background(c.surfaceAlt).border(1.dp, c.border, shape).padding(horizontal = Space.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(right.toString(), style = Ec.t.bodyStrong, color = c.success, modifier = Modifier.padding(horizontal = Space.s2))
        Box(Modifier.size(width = 1.dp, height = 40.dp).background(c.border))
        Text(wrong.toString(), style = Ec.t.bodyStrong, color = c.danger, modifier = Modifier.padding(horizontal = Space.s2))
    }
}

@Composable
private fun PracticeNavStrip(run: PracticeRun, onJump: (Int) -> Unit) {
    val c = Ec.c
    val stripState = rememberLazyListState()
    LaunchedEffect(run.current) { stripState.animateScrollToItem((run.current - 2).coerceAtLeast(0)) }
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        LazyRow(
            state = stripState,
            contentPadding = PaddingValues(horizontal = Space.s4, vertical = Space.s3),
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            itemsIndexed(run.questions, key = { index, q -> "${q.id}#$index" }) { index, q ->
                val outcome = outcomeOf(q, run.answers[q.id])
                val shape = RoundedCornerShape(Radius.sm)
                val (bg, borderCol, fg) = when (outcome) {
                    Outcome.CORRECT -> Triple(c.successLight, c.successBorder, c.success)
                    Outcome.WRONG -> Triple(c.dangerLight, c.dangerBorder, c.danger)
                    Outcome.UNANSWERED -> Triple(c.surface, c.borderStrong, c.textPrimary)
                }
                val isCurrent = index == run.current
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(shape)
                        .background(bg)
                        .border(if (isCurrent) 1.5.dp else 1.dp, if (isCurrent) c.primary else borderCol, shape)
                        .clickable(role = Role.Button) { onJump(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text((index + 1).toString().padStart(2, '0'), style = Ec.t.smallStrong, color = fg)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

@Composable
private fun PracticeQuestionCard(
    index: Int,
    q: PracticeQuestion,
    localized: Boolean,
    selected: Int?,
    current: Boolean,
    onSelect: (Int) -> Unit,
) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.lg)
    val letters = optionLetters(localized)
    val outcome = outcomeOf(q, selected)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, if (current) c.primaryBorder else c.border, shape)
            .padding(Space.s4),
    ) {
        MathText(
            text = q.question,
            style = contentStyle(16f, bold = true),
            color = c.textPrimary,
            modifier = Modifier.fillMaxWidth(),
            prefix = formatQuestionNumber(index, localized),
            prefixColor = c.primary,
        )
        Box(Modifier.height(Space.s3))
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            q.options.forEachIndexed { idx, text ->
                PracticeOptionRow(
                    letter = letters.getOrElse(idx) { "" },
                    text = text,
                    isCorrectOption = outcome != Outcome.UNANSWERED && idx == q.correctAnswer,
                    isWrongPick = outcome == Outcome.WRONG && idx == selected,
                    locked = outcome != Outcome.UNANSWERED,
                    onClick = { onSelect(idx) },
                )
            }
        }
        AnimatedVisibility(
            visible = outcome != Outcome.UNANSWERED && q.explanation.isNotBlank(),
            enter = expandVertically() + fadeIn(),
        ) {
            Column {
                Box(Modifier.height(Space.s4))
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderStrong))
                Row(Modifier.padding(top = Space.s3), horizontalArrangement = Arrangement.spacedBy(Space.s1)) {
                    Text(t("exam.explanationLabel"), style = contentStyle(14f, bold = true), color = c.textPrimary)
                    MathText(q.explanation, contentStyle(14f), c.textPrimary, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PracticeOptionRow(
    letter: String,
    text: String,
    isCorrectOption: Boolean,
    isWrongPick: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.sm)
    val bg = when {
        isCorrectOption -> c.successLight
        isWrongPick -> c.dangerLight
        else -> Color.Transparent
    }
    val borderColor = when {
        isCorrectOption -> c.successBorder
        isWrongPick -> c.dangerBorder
        else -> c.border
    }
    val circleFill = when {
        isCorrectOption -> c.success
        isWrongPick -> c.danger
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 38.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .then(if (locked) Modifier else Modifier.clickable(role = Role.RadioButton, onClick = onClick))
            .padding(horizontal = Space.s3, vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Box(
            Modifier.size(22.dp).background(circleFill, CircleShape)
                .border(1.dp, if (circleFill == Color.Transparent) c.borderStrong else circleFill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                letter,
                style = Ec.t.caption.copy(fontWeight = FontWeight.SemiBold),
                color = if (circleFill == Color.Transparent) c.textPrimary else Color.White,
            )
        }
        MathText(text, contentStyle(14f), c.textPrimary, Modifier.weight(1f))
        if (isCorrectOption || isWrongPick) {
            Box(Modifier.size(20.dp).background(c.surface, CircleShape), contentAlignment = Alignment.Center) {
                Text(
                    if (isCorrectOption) "✓" else "✕",
                    style = Ec.t.caption.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                    color = if (isCorrectOption) c.success else c.danger,
                )
            }
        }
    }
}
