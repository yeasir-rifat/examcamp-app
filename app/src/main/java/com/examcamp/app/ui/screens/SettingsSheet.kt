package com.examcamp.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import com.examcamp.app.data.AiConfig
import com.examcamp.app.data.ExamPrefs
import com.examcamp.app.data.GeminiModels
import com.examcamp.app.ui.components.EcButton
import com.examcamp.app.ui.components.EcDropdown
import com.examcamp.app.ui.components.EcFormGroup
import com.examcamp.app.ui.components.EcIconButton
import com.examcamp.app.ui.components.EcTextField
import com.examcamp.app.ui.components.EcToggle
import com.examcamp.app.ui.theme.EcIcons
import com.examcamp.app.i18n.Lang
import com.examcamp.app.i18n.t
import com.examcamp.app.ui.components.EcSegmented
import com.examcamp.app.ui.theme.Ec
import com.examcamp.app.ui.theme.Radius
import com.examcamp.app.ui.theme.Space
import com.examcamp.app.ui.theme.ThemeMode

/** Settings modal. Appearance is live; AI Configuration and Exam Preferences arrive with the AI step. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    theme: ThemeMode,
    lang: Lang,
    onTheme: (ThemeMode) -> Unit,
    onLang: (Lang) -> Unit,
    aiConfig: AiConfig,
    prefs: ExamPrefs,
    onSave: (AiConfig, ExamPrefs) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Ec.c
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var apiKey by remember(aiConfig) { mutableStateOf(aiConfig.apiKey) }
    var model by remember(aiConfig) { mutableStateOf(if (aiConfig.model.isBlank()) "" else GeminiModels.resolve(aiConfig.model)) }
    var showKey by remember { mutableStateOf(false) }
    var draft by remember(prefs) { mutableStateOf(prefs) }
    var saved by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.s4)) {
            Text(t("settings.title"), style = Ec.t.h3, color = c.textPrimary)
            Box(Modifier.height(Space.s3))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s1)) {
                listOf("settings.tab.appearance", "settings.tab.aiConfig", "settings.tab.examPrefs").forEachIndexed { i, key ->
                    val on = i == tab
                    Box(
                        modifier = Modifier
                            .clickable(role = Role.Tab) { tab = i }
                            .drawBehind {
                                if (on) {
                                    val h = 2.dp.toPx()
                                    drawRect(c.primary, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
                                }
                            }
                            .padding(horizontal = Space.s3, vertical = Space.s3),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(t(key), style = Ec.t.smallStrong, color = if (on) c.primary else c.textSecondary, maxLines = 1)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = Space.s4),
                verticalArrangement = Arrangement.spacedBy(Space.s5),
            ) {
                if (tab == 0) {
                    Field(t("settings.appearance.theme"), t("settings.appearance.themeHint")) {
                        EcSegmented(
                            options = listOf(
                                ThemeMode.LIGHT.code to t("common.light"),
                                ThemeMode.DARK.code to t("common.dark"),
                                ThemeMode.SYSTEM.code to t("common.system"),
                            ),
                            selected = theme.code,
                            onSelect = { onTheme(ThemeMode.from(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            fill = true,
                        )
                    }
                    Field(t("settings.appearance.language"), t("settings.appearance.languageHint")) {
                        EcSegmented(
                            options = listOf(Lang.EN.code to "English", Lang.BN.code to "বাংলা"),
                            selected = lang.code,
                            onSelect = { onLang(Lang.from(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            fill = true,
                        )
                    }
                } else if (tab == 1) {
                    EcFormGroup(t("settings.ai.apiKey")) {
                        EcTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it; saved = false },
                            placeholder = t("settings.ai.apiKeyPlaceholder"),
                            password = !showKey,
                            trailing = {
                                EcIconButton(
                                    icon = if (showKey) EcIcons.EyeOff else EcIcons.Eye,
                                    description = if (showKey) t("settings.ai.hideKey") else t("settings.ai.showKey"),
                                    onClick = { showKey = !showKey },
                                    size = 32.dp,
                                )
                            },
                        )
                        Text(
                            t("settings.ai.getApiKeyLink"),
                            style = Ec.t.caption, color = c.primary,
                            modifier = Modifier.clickable { uriHandler.openUri("https://aistudio.google.com/apikey") },
                        )
                    }
                    EcFormGroup(t("settings.ai.model")) {
                        EcDropdown(
                            options = GeminiModels.options,
                            selected = model,
                            onSelect = { model = it; saved = false },
                            placeholder = t("settings.ai.modelPlaceholder"),
                        )
                    }
                } else {
                    EcFormGroup(t("settings.exam.defaultDifficulty")) {
                        EcDropdown(
                            options = listOf("easy" to t("common.easy"), "medium" to t("common.medium"), "hard" to t("common.hard")),
                            selected = draft.difficulty, onSelect = { draft = draft.copy(difficulty = it); saved = false },
                        )
                    }
                    EcFormGroup(t("settings.exam.defaultLanguage")) {
                        EcDropdown(
                            options = listOf("en" to t("common.langEnglish"), "bn" to t("common.langBengali"), "en-bn" to t("common.langMixed")),
                            selected = draft.language, onSelect = { draft = draft.copy(language = it); saved = false },
                        )
                    }
                    EcFormGroup(t("settings.exam.defaultDuration")) {
                        EcDropdown(
                            options = listOf("10", "20", "30", "60", "90", "120").map { it to t("common.min$it") },
                            selected = draft.duration, onSelect = { draft = draft.copy(duration = it); saved = false },
                        )
                    }
                    ToggleRow(t("home.aiMode.negativeMarkLabel"), draft.negativeMarkEnabled) { draft = draft.copy(negativeMarkEnabled = it); saved = false }
                    EcFormGroup(t("settings.exam.negativeMarkValue")) {
                        EcDropdown(
                            options = listOf("0.25" to t("home.aiMode.negativeMark025"), "0.5" to t("home.aiMode.negativeMark050")),
                            selected = if (draft.negativeMarkValue == 0.5) "0.5" else "0.25",
                            onSelect = { draft = draft.copy(negativeMarkValue = it.toDouble()); saved = false },
                        )
                    }
                    ToggleRow(t("settings.exam.autosave"), draft.autosave) { draft = draft.copy(autosave = it); saved = false }
                    ToggleRow(t("settings.exam.answerKey"), draft.showAnswerKey) { draft = draft.copy(showAnswerKey = it); saved = false }
                }
            }
            if (tab != 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Space.s3),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s3),
                ) {
                    Text(if (saved) t("settings.saved") else "", style = Ec.t.caption, color = c.textMuted, modifier = Modifier.weight(1f))
                    EcButton(
                        t("settings.save"),
                        onClick = { onSave(AiConfig(apiKey.trim(), model), draft); saved = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = Ec.t.label, color = Ec.c.textPrimary, modifier = Modifier.weight(1f))
        EcToggle(checked, onChange)
    }
}

@Composable
private fun Field(label: String, hint: String, control: @Composable () -> Unit) {
    val c = Ec.c
    Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Text(label, style = Ec.t.label, color = c.textPrimary)
        control()
        Text(hint, style = Ec.t.caption, color = c.textMuted)
    }
}
