package com.examcamp.app.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.examcamp.app.data.LiveRecord
import com.examcamp.app.data.PracticeCategory
import com.examcamp.app.data.PracticeEntry
import com.examcamp.app.data.PracticeRow
import com.examcamp.app.data.liveOverview
import com.examcamp.app.data.practiceOverview
import com.examcamp.app.i18n.t
import com.examcamp.app.i18n.tf
import com.examcamp.app.ui.components.EcCard
import com.examcamp.app.ui.components.EcIcon
import com.examcamp.app.ui.theme.Ec
import com.examcamp.app.ui.theme.EcIcons
import com.examcamp.app.ui.theme.Radius
import com.examcamp.app.ui.theme.Space
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.examcamp.app.ui.components.swipeTabs

/** History page (#view-history): Live Exam results and this member's Practice Exams. */
@Composable
fun HistoryScreen(
    practice: List<PracticeRow>,
    live: List<LiveRecord>,
    onOpenPractice: (PracticeRow) -> Unit,
    onDeletePractice: (PracticeRow) -> Unit,
    onUndoDelete: (PracticeRow) -> Unit,
    onHome: () -> Unit,
    onBack: () -> Unit,
) {
    val c = Ec.c
    BackHandler(onBack = onBack)
    var tab by rememberSaveable { mutableStateOf(0) }
    val completed = practice.filter { it.completed }.sortedByDescending { it.dateMs }
    // The row just removed; the Undo bar stays for a few seconds.
    var lastDeleted by remember { mutableStateOf<PracticeRow?>(null) }
    LaunchedEffect(lastDeleted) {
        if (lastDeleted != null) {
            delay(5000)
            lastDeleted = null
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(c.bg)) {
        SubpageHeader(t("nav.history"), onHome, onBack)
        RecordTabs(listOf("history.tab.live", "history.tab.practice"), tab) { tab = it }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .swipeTabs(tab, 2) { tab = it }
                .verticalScroll(rememberScrollState())
                .padding(Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            if (tab == 0) {
                if (live.isEmpty()) EmptyRecord(t("stats.recentResultsLive.empty"))
                else live.forEach { LiveRecordRow(it) }
            } else {
                if (completed.isEmpty()) EmptyRecord(t("history.empty.practice"))
                else {
                    Text(t("history.swipeHint"), style = Ec.t.caption, color = c.textMuted)
                    completed.forEach { row ->
                        key(row.sessionId) {
                            SwipeToDeleteRow(
                                onDelete = {
                                    lastDeleted = row
                                    onDeletePractice(row)
                                },
                            ) { PracticeRecordRow(row) { onOpenPractice(row) } }
                        }
                    }
                }
            }
        }
    }
    lastDeleted?.let { gone ->
        UndoBar(
            Modifier.align(Alignment.BottomCenter).padding(Space.s4),
            onUndo = {
                lastDeleted = null
                onUndoDelete(gone)
            },
        )
    }
    }
}

/** Swipe a row to the left to delete it. The red "Delete" layer is only visible while the row is moving. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteRow(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val c = Ec.c
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        },
        positionalThreshold = { total -> total * 0.4f },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val moving = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Radius.lg))
                    .background(if (moving) c.danger else Color.Transparent)
                    .padding(horizontal = Space.s5),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (moving) Text(t("history.delete"), style = Ec.t.smallStrong, color = Color.White)
            }
        },
    ) { content() }
}

/** "Exam removed from history · Undo". */
@Composable
private fun UndoBar(modifier: Modifier, onUndo: () -> Unit) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.lg)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.textPrimary)
            .padding(start = Space.s4, end = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(t("history.deleted"), style = Ec.t.small, color = c.bg, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onUndo)
                .padding(horizontal = Space.s3),
            contentAlignment = Alignment.Center,
        ) { Text(t("history.undo"), style = Ec.t.smallStrong, color = c.primary) }
    }
}

/** Statistics page (#view-stats): Practice and Live overviews, plus the latest live results. */
@Composable
fun StatisticsScreen(
    practice: List<PracticeRow>,
    live: List<LiveRecord>,
    categories: List<PracticeCategory>,
    progress: Map<String, PracticeEntry>,
    onHome: () -> Unit,
    onBack: () -> Unit,
) {
    val c = Ec.c
    BackHandler(onBack = onBack)
    var tab by rememberSaveable { mutableStateOf(0) }
    var pickedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val activeCategory = categories.firstOrNull { it.id == pickedCategory } ?: categories.firstOrNull()

    Column(Modifier.fillMaxSize().background(c.bg)) {
        SubpageHeader(t("nav.statistics"), onHome, onBack)
        RecordTabs(listOf("stats.tab.practice", "stats.tab.live"), tab) { tab = it }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .swipeTabs(tab, 2) { tab = it }
                .verticalScroll(rememberScrollState())
                .padding(Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            if (tab == 0) {
                OverviewCards(
                    overview = practiceOverview(practice),
                    caption = t("stats.overview.allPractice"),
                    sub = t("stats.overview.acrossPractice"),
                )
                if (categories.size >= 2) {
                    CategoryChips(categories, activeCategory?.id) { pickedCategory = it }
                }
                BcsPrelimCard(activeCategory, progress)
            } else {
                OverviewCards(
                    overview = liveOverview(live),
                    caption = t("stats.overview.allLive"),
                    sub = t("stats.overview.acrossLive"),
                )
                Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    Text(t("stats.recentResults"), style = Ec.t.h4, color = c.textPrimary)
                    if (live.isEmpty()) EmptyRecord(t("stats.recentResultsLive.empty"))
                    else live.take(5).forEach { LiveRecordRow(it) }
                }
            }
        }
    }
}

// ---------- shared pieces ----------

@Composable
private fun RecordTabs(keys: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Ec.c
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            keys.forEachIndexed { i, key ->
                val on = i == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(role = Role.Tab) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                        Text(t(key), style = Ec.t.smallStrong, color = if (on) c.primary else c.textSecondary, maxLines = 1)
                    }
                    Box(Modifier.fillMaxWidth().height(2.dp).background(if (on) c.primary else Color.Transparent))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

@Composable
private fun OverviewCards(overview: com.examcamp.app.data.Overview, caption: String, sub: String) {
    val c = Ec.c
    Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Text(caption, style = Ec.t.h4, color = c.textPrimary)
        Text(sub, style = Ec.t.caption, color = c.textMuted)
    }
    EcCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OverviewStat(overview.count.toString(), t("stats.overview.examsTaken"), Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(40.dp).background(c.border))
            OverviewStat("${overview.avg}%", t("stats.overview.avgScore"), Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(40.dp).background(c.border))
            OverviewStat(overview.questions.toString(), t("stats.overview.questions"), Modifier.weight(1f))
        }
    }
}

@Composable
private fun OverviewStat(value: String, label: String, modifier: Modifier) {
    val c = Ec.c
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s1)) {
        Text(value, style = Ec.t.h3, color = c.textPrimary)
        Text(label, style = Ec.t.caption, color = c.textMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptyRecord(message: String) {
    val c = Ec.c
    Box(Modifier.fillMaxWidth().padding(Space.s6), contentAlignment = Alignment.Center) {
        Text(message, style = Ec.t.small, color = c.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun IconBubble(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, background: Color) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        EcIcon(icon, tint, 20.dp)
    }
}

@Composable
private fun ScorePill(percentage: Double?) {
    val c = Ec.c
    if (percentage == null) {
        Box(
            Modifier.clip(CircleShape).background(c.surfaceAlt).padding(horizontal = Space.s3, vertical = Space.s1),
        ) { Text("-", style = Ec.t.smallStrong, color = c.textSecondary) }
        return
    }
    val (fg, bg, border) = when {
        percentage >= 80 -> Triple(c.success, c.successLight, c.successBorder)
        percentage >= 60 -> Triple(c.primary, c.primaryLight, c.primaryBorder)
        else -> Triple(c.danger, c.dangerLight, c.dangerBorder)
    }
    Box(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .padding(horizontal = Space.s3, vertical = Space.s1),
    ) {
        Text("${fmtPct(percentage)}%", style = Ec.t.smallStrong, color = fg)
    }
}

@Composable
private fun MiniPill(text: String, fg: Color, bg: Color, border: Color) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .padding(horizontal = Space.s2, vertical = Space.s1),
    ) {
        Text(text, style = Ec.t.caption, color = fg)
    }
}

@Composable
private fun PracticeRecordRow(row: PracticeRow, onClick: () -> Unit) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.lg)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Space.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        IconBubble(EcIcons.Practice, c.primary, c.primaryLight)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
            Text(row.subject.ifBlank { "Exam" }, style = Ec.t.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (row.topic.isNotBlank()) {
                Text(row.topic, style = Ec.t.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "${tf("record.questions", row.questionCount)} · ${dateText(row.dateMs)}",
                style = Ec.t.caption,
                color = c.textMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                if (row.evaluate) MiniPill(t("history.badge.evaluate"), c.primary, c.primaryLight, c.primaryBorder)
                MiniPill(t("history.badge.completed"), c.success, c.successLight, c.successBorder)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s1)) {
            ScorePill(row.percentage)
            Text(t("record.score"), style = Ec.t.caption, color = c.textMuted)
        }
        EcIcon(EcIcons.Chevron, c.textMuted, 18.dp)
    }
}

@Composable
private fun LiveRecordRow(record: LiveRecord) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.lg)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.border, shape)
            .padding(Space.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        IconBubble(EcIcons.Live, c.danger, c.dangerLight)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
            Text(record.exam.subject.ifBlank { "Exam" }, style = Ec.t.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (record.exam.topic.isNotBlank()) {
                Text(record.exam.topic, style = Ec.t.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "${tf("record.questions", record.exam.questionCount)} · ${dateText(record.exam.endMs)}",
                style = Ec.t.caption,
                color = c.textMuted,
            )
            MiniPill(t("history.badge.live"), c.danger, c.dangerLight, c.dangerBorder)
        }
        ScorePill(record.percentage)
    }
}

private fun dateText(ms: Long): String =
    if (ms <= 0L) "" else SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(ms))

private fun fmtPct(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.1f", v)
