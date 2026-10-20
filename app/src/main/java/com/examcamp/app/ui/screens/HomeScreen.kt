package com.examcamp.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import com.examcamp.app.AppViewModel
import com.examcamp.app.data.Attachment
import com.examcamp.app.data.ExamConfig
import com.examcamp.app.data.ExamPrefs
import com.examcamp.app.data.Session
import com.examcamp.app.i18n.t
import com.examcamp.app.ui.components.BtnStyle
import com.examcamp.app.ui.components.EcButton
import com.examcamp.app.ui.components.EcCard
import com.examcamp.app.ui.components.EcIcon
import com.examcamp.app.ui.theme.Ec
import com.examcamp.app.ui.theme.EcIcons
import com.examcamp.app.ui.theme.Radius
import com.examcamp.app.ui.theme.Space

/** Mobile home: sticky Dashboard | AI Mode tabs, then the panel for the selected tab. */
@Composable
fun HomeScreen(
    quick: AppViewModel.QuickStats,
    session: Session?,
    aiForm: AiFormState,
    prefs: ExamPrefs,
    onNavigate: (String) -> Unit,
    onGenerate: (ExamConfig, List<Attachment>) -> Unit,
    onOpenSession: () -> Unit,
    showAdmin: Boolean = false,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        HomeTabs(tab) { tab = it }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            if (tab == 0) {
                DashboardCard(quick, onNavigate, showAdmin)
                if (session == null) RecentExamsCard(onCreate = { tab = 1 })
                else ContinueCard(session, onOpenSession)
            } else {
                AiModeCard(aiForm, prefs, onGenerate)
            }
        }
    }
}

@Composable
private fun HomeTabs(selected: Int, onSelect: (Int) -> Unit) {
    val c = Ec.c
    Column(Modifier.fillMaxWidth().background(c.bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.s4)) {
            HomeTab(EcIcons.Grid, t("home.dashboard.title"), selected == 0, Modifier.weight(1f)) { onSelect(0) }
            HomeTab(EcIcons.Sparkle, t("home.aiMode.title"), selected == 1, Modifier.weight(1f)) { onSelect(1) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

@Composable
private fun HomeTab(icon: ImageVector, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ec.c
    val tint = if (active) c.primary else c.textSecondary
    Column(
        modifier = modifier.clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.height(48.dp).padding(horizontal = Space.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s2, Alignment.CenterHorizontally),
        ) {
            EcIcon(icon, tint, 18.dp)
            Text(label, style = Ec.t.smallStrong.copy(fontSize = 15.sp), color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (active) c.primary else Color.Transparent))
    }
}

@Composable
private fun DashboardCard(quick: AppViewModel.QuickStats, onNavigate: (String) -> Unit, showAdmin: Boolean) {
    val c = Ec.c
    EcCard(Modifier.fillMaxWidth(), borderColor = c.primaryBorder) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            EcIcon(EcIcons.Grid, c.primary, 20.dp)
            Text(t("home.dashboard.title").uppercase(), style = Ec.t.h4.copy(letterSpacing = 0.02.em), color = c.textPrimary)
        }
        Box(Modifier.height(Space.s5))
        Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
            DashboardItem(EcIcons.Live, t("home.dashboard.liveTitle"), t("home.dashboard.liveDesc"), live = true) { onNavigate("live-exam") }
            DashboardItem(EcIcons.Practice, t("home.dashboard.practiceTitle"), t("home.dashboard.practiceDesc")) { onNavigate("practice") }
            DashboardItem(EcIcons.History, t("home.dashboard.historyTitle"), t("home.dashboard.historyDesc")) { onNavigate("history") }
            DashboardItem(EcIcons.Stats, t("home.dashboard.statsTitle"), t("home.dashboard.statsDesc")) { onNavigate("statistics") }
            if (showAdmin) {
                DashboardItem(EcIcons.Gear, t("admin.title"), t("admin.homeDesc")) { onNavigate("admin") }
            }
        }
        Box(Modifier.height(Space.s4))
        QuickStats(quick, onNavigate)
    }
}

@Composable
private fun DashboardItem(icon: ImageVector, title: String, desc: String, live: Boolean = false, onClick: () -> Unit) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.lg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surfaceAlt)
            .border(1.dp, c.primaryBorder, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Space.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        val iconShape = RoundedCornerShape(Radius.md)
        Box(
            Modifier.size(44.dp).clip(iconShape).background(c.primaryLight).border(1.5.dp, c.primaryBorder, iconShape),
            contentAlignment = Alignment.Center,
        ) { EcIcon(icon, c.primary, 22.dp) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                Text(title, style = Ec.t.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (live) Box(Modifier.size(8.dp).clip(CircleShape).background(c.danger))
            }
            Text(desc, style = Ec.t.small, color = c.textSecondary, modifier = Modifier.padding(top = Space.s1))
        }
        EcIcon(EcIcons.Chevron, c.textSecondary, 20.dp)
    }
}

@Composable
private fun QuickStats(quick: AppViewModel.QuickStats, onNavigate: (String) -> Unit) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(c.surfaceAlt)
            .border(1.dp, c.primaryBorder, shape),
    ) {
        QuickStat(quick.exams.toString(), t("home.dashboard.quickExams"), Modifier.weight(1f)) { onNavigate("history") }
        Box(Modifier.width(1.dp).fillMaxSize().background(c.primaryBorder))
        QuickStat("${quick.avgPercent}%", t("home.dashboard.quickAvgScore"), Modifier.weight(1f)) { onNavigate("statistics") }
        Box(Modifier.width(1.dp).fillMaxSize().background(c.primaryBorder))
        QuickStat(NumberFormat.getInstance().format(quick.questions), t("home.dashboard.quickQuestions"), Modifier.weight(1f)) { onNavigate("statistics") }
    }
}

@Composable
private fun QuickStat(value: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Ec.c
    Column(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.s1, vertical = Space.s3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(value, style = Ec.t.h4, color = c.primary)
        Text(label, style = Ec.t.caption, color = c.textSecondary, modifier = Modifier.padding(top = Space.s1))
    }
}

@Composable
private fun RecentExamsCard(onCreate: () -> Unit) {
    val c = Ec.c
    EcCard(Modifier.fillMaxWidth(), background = c.warningLight, borderColor = c.warningBorder) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            EcIcon(EcIcons.History, c.textSecondary, 16.dp)
            Text(t("home.continue.emptyTitle"), style = Ec.t.small, color = c.textSecondary)
        }
        Box(Modifier.height(Space.s4))
        Text(t("home.continue.emptyDesc"), style = Ec.t.body, color = c.textPrimary)
        Box(Modifier.height(Space.s4))
        EcButton(
            text = t("home.continue.emptyCta"),
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth(),
            style = BtnStyle.Primary,
        )
    }
}

@Composable
private fun ContinueCard(session: Session, onOpen: () -> Unit) {
    val c = Ec.c
    val completed = session.isCompleted
    val total = session.exam.questions.size
    val answered = session.answers.size
    val pct = if (total > 0) Math.round(answered * 100f / total) else 0
    EcCard(Modifier.fillMaxWidth(), background = c.warningLight, borderColor = c.primaryBorder) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            EcIcon(EcIcons.History, c.textSecondary, 16.dp)
            Text(t("home.continue.titlePractice"), style = Ec.t.body, color = c.textSecondary)
        }
        Box(Modifier.height(Space.s4))
        Text(
            session.exam.subject.ifBlank { "Exam" },
            style = Ec.t.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (session.exam.topic.isNotBlank()) {
            Text(session.exam.topic, style = Ec.t.small, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.height(Space.s2))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$answered / $total ${t("home.continue.questionsCompleted")}", style = Ec.t.small, color = c.textMuted)
            Text("$pct%", style = Ec.t.small, color = c.textMuted)
        }
        Box(Modifier.height(Space.s2))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(Radius.pill)).background(c.hoverBg)) {
            Box(Modifier.fillMaxWidth(pct / 100f).height(8.dp).clip(RoundedCornerShape(Radius.pill)).background(c.primary))
        }
        Box(Modifier.height(Space.s5))
        EcButton(
            text = if (completed) t("home.continue.viewResults") else t("home.continue.continueBtn"),
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth(),
            style = if (completed) BtnStyle.Soft else BtnStyle.Secondary,
        )
    }
}
