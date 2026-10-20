package com.examcamp.app.ui.screens

import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.examcamp.app.util.PdfExporter
import com.examcamp.app.util.ShareHelper
import com.examcamp.app.i18n.I18n
import com.examcamp.app.i18n.LocalLang
import androidx.compose.ui.platform.LocalContext

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examcamp.app.R
import com.examcamp.app.data.GenState
import com.examcamp.app.data.Question
import com.examcamp.app.data.Session
import com.examcamp.app.i18n.t
import com.examcamp.app.i18n.tf
import com.examcamp.app.ui.components.BtnStyle
import com.examcamp.app.ui.components.EcButton
import com.examcamp.app.ui.components.EcCard
import com.examcamp.app.ui.components.EcIcon
import com.examcamp.app.ui.components.EcSpinner
import com.examcamp.app.ui.math.MathText
import com.examcamp.app.ui.theme.Ec
import com.examcamp.app.ui.theme.EcIcons
import com.examcamp.app.ui.theme.Radius
import com.examcamp.app.ui.theme.Space

/** view-generating: spinner while Gemini works, or the error card with Try Again / Use Sample Questions / Back. */
@Composable
fun GeneratingScreen(
    state: GenState,
    onRetry: () -> Unit,
    onSample: () -> Unit,
    onBack: () -> Unit,
) {
    val c = Ec.c
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().padding(Space.s6), contentAlignment = Alignment.Center) {
        EcCard(Modifier.fillMaxWidth(), padding = Space.s6) {
            if (state is GenState.Error) {
                Text(t("generator.errorTitle"), style = Ec.t.h3, color = c.textPrimary)
                Box(Modifier.height(Space.s2))
                Text(
                    if (state.raw.contains("NO_API_KEY")) t("generator.noKey") else tf("generator.failed", state.raw),
                    style = Ec.t.small, color = c.textMuted,
                )
                Box(Modifier.height(Space.s5))
                Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                    EcButton(t("generator.retry"), onRetry, Modifier.fillMaxWidth(), style = BtnStyle.Primary)
                    EcButton(t("generator.useSample"), onSample, Modifier.fillMaxWidth(), style = BtnStyle.Secondary)
                    EcButton(t("generator.backHome"), onBack, Modifier.fillMaxWidth(), style = BtnStyle.Ghost)
                }
            } else {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EcSpinner()
                    Box(Modifier.height(Space.s5))
                    Text(t("generator.title"), style = Ec.t.h3, color = c.textPrimary, textAlign = TextAlign.Center)
                    Box(Modifier.height(Space.s2))
                    Text(t("generator.subtitle"), style = Ec.t.small, color = c.textSecondary, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private fun formatMarks(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format("%.2f", v).trimEnd('0').trimEnd('.')

/** view-stats: score ring, summary, then the answer sheet with the correct option and explanation. */
@Composable
fun ResultsScreen(session: Session, onHome: () -> Unit) {
    val c = Ec.c
    val result = session.result
    BackHandler(onBack = onHome)
    if (result == null) return
    val exam = session.exam
    val localized = usesLocalizedLabels(exam.language)
    val isBn = exam.language == "bn"

    Column(Modifier.fillMaxSize().background(c.bg)) {
        // stats-navbar
        Column(Modifier.fillMaxWidth().background(c.surface).statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(horizontal = Space.s4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s3),
            ) {
                Image(painterResource(R.drawable.brand_icon), null, Modifier.size(24.dp), contentScale = ContentScale.Fit)
                Text(t("brand.name"), style = Ec.t.bodyStrong, color = c.primary, maxLines = 1)
                Column(Modifier.weight(1f)) {
                    Text(t("stats.resultsAnalytics"), style = Ec.t.smallStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val shape = RoundedCornerShape(Radius.md)
                val label = t("stats.backHome")
                Box(
                    Modifier.size(40.dp).clip(shape).background(c.surface).border(1.dp, c.primaryBorder, shape)
                        .clickable(role = Role.Button, onClickLabel = label, onClick = onHome),
                    contentAlignment = Alignment.Center,
                ) { EcIcon(EcIcons.Home, c.primary, 20.dp) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(Space.s4),
        ) {
            Text(exam.subject.ifBlank { "Exam" }, style = Ec.t.h2, color = c.textPrimary)
            if (exam.topic.isNotBlank()) Text(exam.topic, style = Ec.t.small, color = c.textSecondary)
            Box(Modifier.height(Space.s4))

            HeadlineCard(session)
            ExportRow(session)

            Row(Modifier.padding(top = Space.s10, bottom = Space.s4), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                Text(t("stats.answerSheet"), style = Ec.t.h3, color = c.textPrimary)
                Text("(${result.totalQuestions})", style = Ec.t.small, color = c.textMuted, modifier = Modifier.padding(bottom = 3.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
                exam.questions.forEachIndexed { index, q ->
                    ReviewCard(index, q, session.answers[q.id], localized)
                }
            }
            Box(Modifier.height(Space.s8))
        }
    }
}

@Composable
private fun HeadlineCard(session: Session) {
    val c = Ec.c
    val r = session.result ?: return
    val shape = RoundedCornerShape(Radius.lg)
    val narrow = LocalConfiguration.current.screenWidthDp <= 560
    val ring: @Composable () -> Unit = { ScoreRing(r.percentage.toFloat()) }
    val body: @Composable (Alignment.Horizontal) -> Unit = { align ->
        Column(horizontalAlignment = align) {
            Text(t("stats.obtainedMarks"), style = Ec.t.small, color = c.textMuted)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                Text(formatMarks(r.obtainedMarks), style = Ec.t.h1, color = c.textPrimary)
                Text("/ ${formatMarks(r.totalMarks)}", style = Ec.t.h4.copy(fontWeight = FontWeight.Medium), color = c.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
            }
            Text(
                tf("stats.summary", r.correct, r.wrong, r.unanswered, r.attempted, formatDuration(r.timeUsed)),
                style = Ec.t.small, color = c.textSecondary,
                textAlign = if (narrow) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.padding(top = Space.s2),
            )
        }
    }
    Box(
        Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.primaryBorder, shape).padding(Space.s4),
    ) {
        if (narrow) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s5)) {
                ring()
                body(Alignment.CenterHorizontally)
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s8)) {
                ring()
                Box(Modifier.weight(1f)) { body(Alignment.Start) }
            }
        }
    }
}

/** headline-ring: 128dp, 10dp stroke, primary arc that animates from empty over 700ms. */
@Composable
private fun ScoreRing(percentage: Float) {
    val c = Ec.c
    val target = (percentage / 100f).coerceIn(0f, 1f)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) { anim.animateTo(target, tween(700)) }
    val track = c.border
    val arc = c.primary
    Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(128.dp)) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            val sz = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(stroke))
            drawArc(arc, -90f, 360f * anim.value, false, Offset(inset, inset), sz, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("${formatMarks(percentage.toDouble())}%", style = Ec.t.h3, color = c.primary)
    }
}

@Composable
private fun ReviewCard(index: Int, q: Question, selected: Int?, localized: Boolean) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.lg)
    val letters = optionLetters(localized)
    val unanswered = selected == null
    val wrong = selected != null && selected != q.correctAnswer
    Column(Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.border, shape).padding(Space.s4)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s3), verticalAlignment = Alignment.Top) {
            MathText(
                text = q.question,
                style = contentStyle(16f, bold = true), color = c.textPrimary, modifier = Modifier.weight(1f),
                prefix = formatQuestionNumber(index, localized), prefixColor = c.primary,
            )
            if (unanswered) {
                val label = t("exam.notAnswered")
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(c.warningLight).border(1.dp, c.warningBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { EcIcon(EcIcons.HelpCircle, c.warning, 16.dp, Modifier.padding(0.dp)) }
            }
        }
        Box(Modifier.height(Space.s3))
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            q.options.forEachIndexed { idx, text ->
                val isCorrect = idx == q.correctAnswer
                val isWrongPick = idx == selected && wrong
                val letterBorder = when {
                    isCorrect -> c.successBorder
                    isWrongPick -> c.dangerBorder
                    else -> c.borderStrong
                }
                val letterColor = when {
                    isCorrect -> c.success
                    isWrongPick -> c.danger
                    else -> c.textPrimary
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 38.dp).padding(horizontal = Space.s3, vertical = Space.s2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s3),
                ) {
                    Box(Modifier.size(22.dp).border(1.dp, letterBorder, CircleShape), contentAlignment = Alignment.Center) {
                        Text(letters.getOrElse(idx) { "" }, style = Ec.t.caption.copy(fontWeight = FontWeight.SemiBold), color = letterColor)
                    }
                    MathText(text, contentStyle(14f), c.textPrimary, Modifier.weight(1f))
                    if (isCorrect || isWrongPick) {
                        Box(
                            Modifier.size(20.dp).background(if (isCorrect) c.successLight else c.dangerLight, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (isCorrect) "✓" else "✕", style = Ec.t.caption.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp), color = letterColor)
                        }
                    }
                }
            }
        }
        if (q.explanation.isNotBlank()) {
            Box(Modifier.height(Space.s4))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderStrong))
            Row(Modifier.padding(top = Space.s3), horizontalArrangement = Arrangement.spacedBy(Space.s1)) {
                Text(
                    t("exam.explanationLabel"),
                    style = contentStyle(14f, bold = true), color = c.textPrimary,
                )
                MathText(q.explanation, contentStyle(14f), c.textPrimary, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ExportRow(session: Session) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = LocalLang.current
    val lResult = I18n.get(lang, "export.resultPdf")
    val lPaper = I18n.get(lang, "export.paperPdf")
    val lImage = I18n.get(lang, "export.shareImage")
    val failed = I18n.get(lang, "export.failed")
    val chooser = I18n.get(lang, "export.share")

    fun exportAndShare(build: () -> java.io.File, share: (java.io.File) -> Unit) {
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { build() }
                share(file)
            } catch (e: Exception) {
                Toast.makeText(ctx, failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    var showPrefs by remember { mutableStateOf(false) }
    if (showPrefs) {
        // #print-prefs-modal: "Select your download preferences?" with Answer Sheet / Explanation Sheet.
        val shape = RoundedCornerShape(Radius.lg)
        val c = Ec.c
        Dialog(onDismissRequest = { showPrefs = false }) {
            Box(Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.border, shape)) {
                Column(Modifier.fillMaxWidth().padding(Space.s5)) {
                    Text(
                        I18n.get(lang, "stats.downloadPrefs.title"),
                        style = Ec.t.h3,
                        color = c.textPrimary,
                        modifier = Modifier.padding(end = Space.s8),
                    )
                    Box(Modifier.height(Space.s5))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                        EcButton(
                            I18n.get(lang, "stats.downloadPrefs.answerSheet"),
                            onClick = {
                                showPrefs = false
                                exportAndShare({ PdfExporter.markedSheet(ctx, session, lang, includeExplanations = false) }) { ShareHelper.sharePdf(ctx, it, chooser) }
                            },
                            style = BtnStyle.Secondary,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp), // .btn-lg
                        )
                        EcButton(
                            I18n.get(lang, "stats.downloadPrefs.explanationSheet"),
                            onClick = {
                                showPrefs = false
                                exportAndShare({ PdfExporter.markedSheet(ctx, session, lang, includeExplanations = true) }) { ShareHelper.sharePdf(ctx, it, chooser) }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        )
                    }
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(Space.s3).size(36.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .clickable(role = Role.Button, onClickLabel = I18n.get(lang, "common.close")) { showPrefs = false },
                    contentAlignment = Alignment.Center,
                ) { EcIcon(EcIcons.Close, c.textSecondary, 18.dp) }
            }
        }
    }

    Row(Modifier.fillMaxWidth().padding(bottom = Space.s4), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
        EcButton(
            lResult,
            onClick = {
                exportAndShare({ PdfExporter.resultReport(ctx, session, lang) }) { ShareHelper.sharePdf(ctx, it, chooser) }
            },
            small = true,
            modifier = Modifier.weight(1f),
        )
        EcButton(
            lPaper,
            onClick = { showPrefs = true },
            small = true,
            modifier = Modifier.weight(1f),
        )
        EcButton(
            lImage,
            onClick = {
                exportAndShare({ ShareHelper.resultCard(ctx, session) }) { ShareHelper.sharePng(ctx, it, chooser) }
            },
            small = true,
            modifier = Modifier.weight(1f),
        )
    }
}
