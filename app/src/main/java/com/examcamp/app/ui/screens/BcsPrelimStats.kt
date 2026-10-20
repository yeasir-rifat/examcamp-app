package com.examcamp.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.examcamp.app.data.PracticeCategory
import com.examcamp.app.data.PracticeEntry
import com.examcamp.app.data.practiceKey
import com.examcamp.app.i18n.t
import com.examcamp.app.ui.components.EcIcon
import com.examcamp.app.ui.theme.Ec
import com.examcamp.app.ui.theme.EcIcons
import com.examcamp.app.ui.theme.Radius
import com.examcamp.app.ui.theme.Space

/** Same palette as BCS_PRELIM_PALETTE in app.js. */
private val BcsPalette = listOf(
    Color(0xFF6366F1), Color(0xFFF59E0B), Color(0xFF10B981), Color(0xFFEF4444),
    Color(0xFF3B82F6), Color(0xFFA855F7), Color(0xFFEC4899), Color(0xFF14B8A6),
    Color(0xFF84CC16), Color(0xFFF97316), Color(0xFF0EA5E9), Color(0xFF8B5CF6),
)

/** One subject of the chosen category: total attempts and attempts per topic (name, count). */
data class SubjectAttempt(
    val id: String,
    val name: String,
    val color: Color,
    val attempted: Int,
    val topics: List<Pair<String, Int>>,
)

/** Attempts per topic, read from saved Practice progress (bcsPrelimAttemptedForTopic in app.js). */
private fun attemptsFor(progress: Map<String, PracticeEntry>, subjectId: String, topicId: String): Int {
    val entry = progress[practiceKey(subjectId, topicId)] ?: return 0
    return if (entry.answeredCount > 0) entry.answeredCount else entry.answers.size
}

fun bcsSubjectRows(category: PracticeCategory?, progress: Map<String, PracticeEntry>): List<SubjectAttempt> {
    val subjects = category?.subjects.orEmpty()
    return subjects.mapIndexed { i, s ->
        val topics = s.topics.map { it.name to attemptsFor(progress, s.id, it.id) }
        SubjectAttempt(
            id = s.id,
            name = s.name,
            color = BcsPalette[i % BcsPalette.size],
            attempted = topics.sumOf { it.second },
            topics = topics,
        )
    }
}

/**
 * BCS Preliminary card: donut of attempts per subject, and under it a legend. Tapping a subject
 * opens its per-topic bar chart. Only real Practice activity is shown, no sample numbers.
 */
@Composable
fun BcsPrelimCard(category: PracticeCategory?, progress: Map<String, PracticeEntry>) {
    val c = Ec.c
    val rows = remember(category, progress) { bcsSubjectRows(category, progress) }
    val total = rows.sumOf { it.attempted }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val shape = RoundedCornerShape(Radius.lg)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.border, shape)
            .padding(Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Text(category?.name ?: t("stats.bcsPrelim.title"), style = Ec.t.h4, color = c.textPrimary)
        Text(t("stats.bcsPrelim.subtitle"), style = Ec.t.caption, color = c.textMuted)

        if (total == 0) {
            Text(t("stats.bcsPrelim.empty"), style = Ec.t.small, color = c.textSecondary)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Donut(rows, total)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                rows.forEach { row ->
                    LegendRow(
                        row = row,
                        total = total,
                        open = openId == row.id,
                        onToggle = { openId = if (openId == row.id) null else row.id },
                    )
                }
            }
        }
    }
}

@Composable
private fun Donut(rows: List<SubjectAttempt>, total: Int) {
    val c = Ec.c
    Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.22f
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            var start = -90f
            rows.filter { it.attempted > 0 }.forEach { r ->
                val sweep = r.attempted.toFloat() / total * 360f
                drawArc(
                    color = r.color,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(total.toString(), style = Ec.t.h3, color = c.textPrimary)
            Text(t("stats.bcsPrelim.totalAttempted"), style = Ec.t.caption, color = c.textMuted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LegendRow(row: SubjectAttempt, total: Int, open: Boolean, onToggle: () -> Unit) {
    val c = Ec.c
    val empty = row.attempted == 0
    val pct = if (total > 0) Math.round(row.attempted * 100.0 / total).toInt() else 0
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(vertical = Space.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(row.color))
            Text(
                row.name,
                style = Ec.t.small,
                color = if (empty) c.textMuted else c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("$pct%", style = Ec.t.smallStrong, color = c.textSecondary)
            EcIcon(EcIcons.ChevronDown, c.textSecondary, 16.dp, Modifier.rotate(if (open) 180f else 0f))
        }
        if (open) TopicBars(row)
    }
}

@Composable
private fun TopicBars(row: SubjectAttempt) {
    val c = Ec.c
    val active = row.topics.filter { it.second > 0 }
    if (active.isEmpty()) {
        Text(
            t("stats.bcsPrelim.noTopicActivity"),
            style = Ec.t.small,
            color = c.textSecondary,
            modifier = Modifier.padding(vertical = Space.s2),
        )
        return
    }
    val max = active.maxOf { it.second }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = Space.s2, bottom = Space.s2),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        row.topics.forEach { (name, n) ->
            val target = if (n == 0) 0f else maxOf(n.toFloat() / max, 0.04f)
            val fraction by animateFloatAsState(targetValue = target, animationSpec = tween(500), label = "bcsBar")
            Column(
                Modifier.width(64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.s1),
            ) {
                Text(n.toString(), style = Ec.t.caption, color = c.textPrimary)
                Box(
                    Modifier
                        .width(28.dp)
                        .height(120.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(c.surfaceAlt),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp * fraction)
                            .background(row.color),
                    )
                }
                Text(
                    name,
                    style = Ec.t.caption,
                    color = c.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Category picker above the Practice tab cards (the website's stats-category-dropdown, as a chip row). */
@Composable
fun CategoryChips(categories: List<PracticeCategory>, activeId: String?, onPick: (String) -> Unit) {
    val c = Ec.c
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        categories.forEach { cat ->
            val on = cat.id == activeId
            val shape = RoundedCornerShape(Radius.pill)
            Row(
                Modifier
                    .clip(shape)
                    .background(if (on) c.primaryLight else c.surface)
                    .border(1.dp, if (on) c.primaryBorder else c.border, shape)
                    .clickable(role = Role.Tab) { onPick(cat.id) }
                    .padding(horizontal = Space.s3, vertical = Space.s2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s2),
            ) {
                if (cat.icon.isNotBlank() && !cat.icon.startsWith("data:")) {
                    Text(cat.icon, style = Ec.t.small)
                }
                Text(cat.name, style = Ec.t.smallStrong, color = if (on) c.primary else c.textSecondary, maxLines = 1)
            }
        }
    }
}
