package com.examcamp.app.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.examcamp.app.R
import com.examcamp.app.data.PracticeCategory
import com.examcamp.app.data.PracticeEngine
import com.examcamp.app.data.PracticeOpenError
import com.examcamp.app.data.PracticeQuestion
import com.examcamp.app.data.PracticeSubject
import com.examcamp.app.data.PracticeTopic
import com.examcamp.app.i18n.t
import com.examcamp.app.i18n.tf
import com.examcamp.app.ui.components.BtnStyle
import com.examcamp.app.ui.components.EcButton
import com.examcamp.app.ui.components.EcIcon
import com.examcamp.app.ui.components.EcIconButton
import com.examcamp.app.ui.components.EcSpinner
import com.examcamp.app.ui.theme.Ec
import com.examcamp.app.ui.theme.EcIcons
import com.examcamp.app.ui.theme.Radius
import com.examcamp.app.ui.theme.Space
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// ---------- shared pieces ----------

/** .subpage-header: brand on the left; page label (wide screens only), Home and Back on the right. */
@Composable
fun SubpageHeader(pageLabel: String?, onHome: () -> Unit, onBack: () -> Unit) {
    val c = Ec.c
    val compact = LocalConfiguration.current.screenWidthDp <= 640
    Column(Modifier.fillMaxWidth().background(c.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(if (compact) 64.dp else 72.dp).padding(horizontal = if (compact) Space.s4 else Space.s6),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s2),
            ) {
                Image(painterResource(R.drawable.brand_icon), null, Modifier.size(36.dp), contentScale = ContentScale.Fit)
                Text(
                    t("brand.name"),
                    style = (if (compact) Ec.t.bodyStrong else Ec.t.h4).copy(letterSpacing = (-0.02).em),
                    color = c.primary, maxLines = 1, overflow = TextOverflow.Clip,
                )
            }
            if (!compact && pageLabel != null) {
                Text(pageLabel, style = Ec.t.h4, color = c.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val size = if (compact) 36.dp else 44.dp
            EcIconButton(EcIcons.Home, t("practice.backHome"), onHome, size = size)
            EcIconButton(EcIcons.ChevronLeft, t("practice.back"), onBack, size = size)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

/** A category icon is either an emoji or an admin-uploaded "data:image/..." picture. */
@Composable
private fun CategoryIcon(icon: String, imageSize: Int, emojiSize: Int) {
    val bitmap = remember(icon) {
        if (icon.startsWith("data:image")) {
            try {
                val bytes = Base64.decode(icon.substringAfter(","), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) { null }
        } else null
    }
    if (bitmap != null) {
        Image(
            painter = BitmapPainter(bitmap),
            contentDescription = null,
            modifier = Modifier.size(imageSize.dp).clip(RoundedCornerShape(Radius.md)),
            contentScale = ContentScale.Crop,
        )
    } else {
        val emoji = if (icon.startsWith("data:image") || icon.isBlank()) "📚" else icon
        Text(emoji, fontSize = emojiSize.sp, lineHeight = emojiSize.sp)
    }
}

@Composable
private fun Badge(text: String, primary: Boolean) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.sm)
    Box(
        Modifier.clip(shape)
            .background(if (primary) c.primaryLight else c.hoverBg)
            .border(1.dp, if (primary) c.primaryBorder else c.border, shape)
            .padding(horizontal = Space.s3, vertical = Space.s1),
    ) {
        Text(
            text, style = Ec.t.caption.copy(fontWeight = FontWeight.SemiBold),
            color = if (primary) c.primary else c.textSecondary,
        )
    }
}

// ---------- level 1: category grid (#view-practice) ----------

@Composable
fun PracticeCategoriesScreen(
    categories: List<PracticeCategory>,
    onPick: (String) -> Unit,
    onHome: () -> Unit,
    onBack: () -> Unit,
) {
    val c = Ec.c
    BackHandler(onBack = onBack)
    val columns = if (LocalConfiguration.current.screenWidthDp >= 640) 3 else 2
    Column(Modifier.fillMaxSize().background(c.bg)) {
        SubpageHeader(t("practice.pageTitle"), onHome, onBack)
        if (categories.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(Space.s6), contentAlignment = Alignment.Center) {
                Text(t("practice.category.empty"), style = Ec.t.small, color = c.textSecondary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(Space.s4),
                horizontalArrangement = Arrangement.spacedBy(Space.s3),
                verticalArrangement = Arrangement.spacedBy(Space.s3),
            ) {
                items(categories, key = { it.id }) { cat -> CategoryCard(cat) { onPick(cat.id) } }
            }
        }
    }
}

@Composable
private fun CategoryCard(cat: PracticeCategory, onClick: () -> Unit) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.lg)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.primaryBorder, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Space.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s2, Alignment.CenterVertically),
    ) {
        CategoryIcon(cat.icon, imageSize = 40, emojiSize = 32)
        Text(cat.name, style = Ec.t.bodyStrong, color = c.primary, textAlign = TextAlign.Center)
        if (cat.description.isNotBlank()) {
            Text(cat.description, style = Ec.t.caption, color = c.textMuted, textAlign = TextAlign.Center)
        }
    }
}

// ---------- level 2: subject / topic accordion (#view-practice-subjects) ----------

private sealed interface PracticeDialog {
    data class Interest(val subject: PracticeSubject, val topic: PracticeTopic, val subtopic: String?) : PracticeDialog
    data class Subtopic(val subject: PracticeSubject, val topic: PracticeTopic, val total: Int, val rows: List<Pair<String, Int>>) : PracticeDialog
    data class Modules(val subject: PracticeSubject, val topic: PracticeTopic, val modules: List<List<PracticeQuestion>>) : PracticeDialog
}

@Composable
fun PracticeSubjectsScreen(
    category: PracticeCategory,
    engine: PracticeEngine,
    onStartPractice: () -> Unit,
    onStartEvaluate: (PracticeSubject, PracticeTopic, List<PracticeQuestion>, Int) -> Unit,
    onHome: () -> Unit,
    onBack: () -> Unit,
) {
    val c = Ec.c
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    var openSubject by remember(category.id) { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<PracticeDialog?>(null) }
    var busy by remember { mutableStateOf(false) }

    val notFoundMsg = t("practice.err.notFound")
    val loadFailedMsg = t("practice.err.load")
    val emptyMsg = t("practice.err.empty")
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    // Topic tap (openPracticeTopicEntry): banks with subtopics ask which one first.
    fun onTopic(subject: PracticeSubject, topic: PracticeTopic) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                val questions = try {
                    engine.loadQuestions(subject.id, topic.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    dialog = PracticeDialog.Interest(subject, topic, null)
                    return@launch
                }
                val counts = engine.subtopicCounts(questions)
                dialog = if (counts.isEmpty()) PracticeDialog.Interest(subject, topic, null)
                else PracticeDialog.Subtopic(subject, topic, questions.size, counts)
            } finally {
                busy = false
            }
        }
    }

    fun startPractice(subject: PracticeSubject, topic: PracticeTopic, subtopic: String?) {
        dialog = null
        scope.launch {
            busy = true
            try {
                when (engine.openRun(subject.id, topic.id, subtopic)) {
                    null -> onStartPractice()
                    PracticeOpenError.NOT_FOUND -> toast(notFoundMsg)
                    PracticeOpenError.LOAD_FAILED -> toast(loadFailedMsg)
                    PracticeOpenError.EMPTY -> toast(emptyMsg)
                }
            } finally {
                busy = false
            }
        }
    }

    fun startEvaluate(subject: PracticeSubject, topic: PracticeTopic) {
        dialog = null
        scope.launch {
            busy = true
            try {
                val questions = try {
                    engine.loadQuestions(subject.id, topic.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    toast(loadFailedMsg)
                    return@launch
                }
                if (questions.isEmpty()) toast(emptyMsg)
                else dialog = PracticeDialog.Modules(subject, topic, engine.evaluateModules(questions))
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        SubpageHeader(category.name, onHome, onBack)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            item(key = "heading") {
                Row(
                    Modifier.padding(bottom = Space.s2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s3),
                ) {
                    CategoryIcon(category.icon, imageSize = 36, emojiSize = 27)
                    Text(category.name, style = Ec.t.h4, color = c.textPrimary)
                }
            }
            item(key = "offline") {
                OfflineDownloadCard(category, engine) { toast(it) }
            }
            items(category.subjects, key = { it.id }) { subject ->
                SubjectCard(
                    subject = subject,
                    open = openSubject == subject.id,
                    onToggle = { openSubject = if (openSubject == subject.id) null else subject.id },
                    onTopic = { onTopic(subject, it) },
                )
            }
        }
    }

    when (val d = dialog) {
        is PracticeDialog.Subtopic -> SubtopicDialog(
            total = d.total,
            rows = d.rows,
            onPick = { name -> dialog = PracticeDialog.Interest(d.subject, d.topic, name) },
            onDismiss = { dialog = null },
        )
        is PracticeDialog.Interest -> InterestDialog(
            subject = d.subject.name,
            topic = d.topic.name,
            showEvaluate = d.subtopic == null, // Evaluate works on the whole bank
            onPractice = { startPractice(d.subject, d.topic, d.subtopic) },
            onEvaluate = { startEvaluate(d.subject, d.topic) },
            onDismiss = { dialog = null },
        )
        is PracticeDialog.Modules -> ModuleDialog(
            subject = d.subject.name,
            topic = d.topic.name,
            sizes = d.modules.map { it.size },
            onPick = { index ->
                dialog = null
                onStartEvaluate(d.subject, d.topic, d.modules[index], index + 1)
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }

    if (busy) {
        Dialog(onDismissRequest = {}) { Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) { EcSpinner() } }
    }
}

/** "Download all for offline": saves every topic's question bank of this category on the phone. */
@Composable
private fun OfflineDownloadCard(category: PracticeCategory, engine: PracticeEngine, toast: (String) -> Unit) {
    val c = Ec.c
    val dl by engine.download.collectAsStateWithLifecycle()
    val mine = dl.categoryId == category.id
    var savedCount by remember(category.id) { mutableStateOf(0) }
    val total = category.subjects.sumOf { it.topics.size }
    val needOnline = t("offline.dl.needOnline")
    // Recount from disk when the screen opens and whenever a run ends.
    LaunchedEffect(category.id, dl.running, dl.saved) { savedCount = engine.savedTopicCount(category) }
    if (total == 0) return

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
        when {
            mine && dl.running -> {
                Text(tf("offline.dl.running", dl.saved + dl.failed, dl.total), style = Ec.t.smallStrong, color = c.textPrimary)
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(Radius.pill)).background(c.border)) {
                    Box(
                        Modifier
                            .fillMaxWidth(((dl.saved + dl.failed).toFloat() / dl.total.coerceAtLeast(1)).coerceIn(0f, 1f))
                            .height(6.dp)
                            .background(c.primary),
                    )
                }
                EcButton(t("offline.dl.cancel"), onClick = { engine.cancelDownload() }, style = BtnStyle.Secondary, small = true)
            }
            mine && dl.finished && dl.failed == 0 -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    EcIcon(EcIcons.Check, c.success, 18.dp)
                    Text(tf("offline.dl.done", dl.saved), style = Ec.t.small, color = c.textPrimary, modifier = Modifier.weight(1f))
                }
            }
            mine && dl.finished -> {
                Text(tf("offline.dl.partial", dl.saved, dl.failed), style = Ec.t.small, color = c.danger)
                EcButton(
                    t("offline.dl.retry"),
                    onClick = { if (engine.canDownload()) engine.downloadAll(category) else toast(needOnline) },
                    style = BtnStyle.Soft,
                    small = true,
                )
            }
            else -> {
                Text(tf("offline.dl.saved", savedCount, total), style = Ec.t.caption, color = c.textMuted)
                EcButton(
                    t("offline.dl.button"),
                    onClick = { if (engine.canDownload()) engine.downloadAll(category) else toast(needOnline) },
                    style = BtnStyle.Soft,
                    small = true,
                )
            }
        }
    }
}

@Composable
private fun SubjectCard(subject: PracticeSubject, open: Boolean, onToggle: () -> Unit, onTopic: (PracticeTopic) -> Unit) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.lg)
    val chevron by animateFloatAsState(if (open) 180f else 0f, label = "subjectChevron")
    Column(Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.primaryBorder, shape)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (open) c.primaryLight else c.primaryWash)
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(Space.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            Text(
                subject.name, style = Ec.t.bodyStrong, color = c.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            EcIcon(EcIcons.ChevronDown, c.primary, 20.dp, Modifier.rotate(chevron))
        }
        AnimatedVisibility(visible = open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(
                Modifier.fillMaxWidth().background(c.surfaceAlt).padding(Space.s2),
                verticalArrangement = Arrangement.spacedBy(Space.s2),
            ) {
                val grouped = subject.mainTopics
                grouped.forEach { main ->
                    MainTopicGroup(main.name, subject.topics.filter { it.mainTopicId == main.id }, onTopic)
                }
                val loose = subject.topics.filter { tp -> grouped.none { it.id == tp.mainTopicId } }
                loose.forEach { TopicRow(it.name) { onTopic(it) } }
                if (grouped.isEmpty() && loose.isEmpty()) {
                    Text(t("practice.noTopics"), style = Ec.t.small, color = c.textSecondary, modifier = Modifier.padding(Space.s3))
                }
            }
        }
    }
}

/** A Main Topic: closed-by-default dropdown with its topics indented under a 2dp rule. */
@Composable
private fun MainTopicGroup(name: String, topics: List<PracticeTopic>, onTopic: (PracticeTopic) -> Unit) {
    val c = Ec.c
    var open by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (open) 180f else 0f, label = "mainChevron")
    Column(Modifier.fillMaxWidth().padding(top = Space.s2)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.md))
                .clickable(role = Role.Button) { open = !open }
                .padding(horizontal = Space.s4, vertical = Space.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            Text(name, style = Ec.t.bodyStrong.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary, modifier = Modifier.weight(1f))
            EcIcon(EcIcons.ChevronDown, c.textSecondary, 18.dp, Modifier.rotate(chevron))
        }
        AnimatedVisibility(visible = open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val rule = c.border
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Space.s4, bottom = Space.s1)
                    .drawBehind { drawLine(rule, Offset(1.dp.toPx(), 0f), Offset(1.dp.toPx(), size.height), strokeWidth = 2.dp.toPx()) }
                    .padding(start = Space.s3),
                verticalArrangement = Arrangement.spacedBy(Space.s3),
            ) {
                if (topics.isEmpty()) {
                    Text(t("practice.noTopics"), style = Ec.t.small, color = c.textSecondary, modifier = Modifier.padding(Space.s3))
                }
                topics.forEach { tp -> TopicRow(tp.name) { onTopic(tp) } }
            }
        }
    }
}

@Composable
private fun TopicRow(name: String, onClick: () -> Unit) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.md)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.primaryBorder, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.s3, vertical = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = Ec.t.body.copy(fontWeight = FontWeight.Medium), color = c.textSecondary, modifier = Modifier.weight(1f))
    }
}

// ---------- popups ----------

@Composable
private fun PopupCard(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.lg)
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.border, shape)) {
            Column(Modifier.fillMaxWidth().padding(Space.s5)) {
                Text(title, style = Ec.t.h4, color = c.textPrimary, modifier = Modifier.padding(end = Space.s8))
                Box(Modifier.height(Space.s3))
                content()
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(Space.s3).size(36.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .clickable(role = Role.Button, onClickLabel = t("common.close"), onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { EcIcon(EcIcons.Close, c.textSecondary, 18.dp) }
        }
    }
}

@Composable
private fun BadgeRow(subject: String, topic: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2), modifier = Modifier.fillMaxWidth()) {
        Badge(subject, primary = true)
        Badge(topic, primary = false)
    }
}

/** "Select your interest?" popup (#practice-interest-modal). */
@Composable
private fun InterestDialog(
    subject: String,
    topic: String,
    showEvaluate: Boolean,
    onPractice: () -> Unit,
    onEvaluate: () -> Unit,
    onDismiss: () -> Unit,
) {
    PopupCard(t("practice.interest.title"), onDismiss) {
        BadgeRow(subject, topic)
        Box(Modifier.height(Space.s5))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
            EcButton(t("practice.interest.practice"), onPractice, Modifier.weight(1f), style = BtnStyle.Secondary)
            if (showEvaluate) EcButton(t("practice.interest.evaluate"), onEvaluate, Modifier.weight(1f), style = BtnStyle.Primary)
        }
    }
}

/** "Choose subtopic" popup: "All questions" first, then each subtopic with its question count. */
@Composable
private fun SubtopicDialog(total: Int, rows: List<Pair<String, Int>>, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val c = Ec.c
    PopupCard(t("practice.subtopic.title"), onDismiss) {
        LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            item(key = "all") { SubtopicRow(t("practice.subtopic.all"), total) { onPick(null) } }
            items(rows, key = { it.first }) { (name, count) -> SubtopicRow(name, count) { onPick(name) } }
        }
    }
}

@Composable
private fun SubtopicRow(label: String, count: Int, onClick: () -> Unit) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.sm)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .border(1.dp, c.textPrimary, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.s4, vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Text(label, style = Ec.t.button, color = c.textPrimary, modifier = Modifier.weight(1f))
        Text(count.toString(), style = Ec.t.small, color = c.textSecondary)
    }
}

/** "Select your module?" popup: modules in two columns (#practice-module-modal). */
@Composable
private fun ModuleDialog(subject: String, topic: String, sizes: List<Int>, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    PopupCard(t("practice.module.title"), onDismiss) {
        BadgeRow(subject, topic)
        Box(Modifier.height(Space.s5))
        LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
            items(sizes.indices.chunked(2), key = { it.first() }) { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                    pair.forEach { i -> ModuleButton(i + 1, sizes[i], Modifier.weight(1f)) { onPick(i) } }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ModuleButton(number: Int, size: Int, modifier: Modifier, onClick: () -> Unit) {
    val c = Ec.c
    val shape = RoundedCornerShape(Radius.sm)
    Column(
        modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .border(1.dp, c.textPrimary, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.s4, vertical = Space.s3),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(tf("practice.module.name", number), style = Ec.t.button, color = c.textPrimary)
        Text(tf("practice.module.questions", size), style = Ec.t.caption, color = c.textSecondary)
    }
}
