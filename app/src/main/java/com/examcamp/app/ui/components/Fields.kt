package com.examcamp.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.examcamp.app.ui.theme.Ec
import com.examcamp.app.ui.theme.EcIcons
import com.examcamp.app.ui.theme.Radius
import com.examcamp.app.ui.theme.Space

/** Border colour of a field: error, focused, or resting. [tinted] is the brand-tinted look used inside the AI Mode card. */
@Composable
private fun fieldBorder(error: Boolean, focused: Boolean, tinted: Boolean): Color {
    val c = Ec.c
    return when {
        error -> c.danger
        focused -> c.primary
        // The website's last CSS layer gives every field, AI Mode card included, the same neutral rest border.
        else -> c.border
    }
}

/** .form-control: 44dp tall, 6dp radius, 1dp border that turns primary on focus. */
@Composable
fun EcTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isError: Boolean = false,
    singleLine: Boolean = true,
    height: Dp = 44.dp,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    tinted: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Ec.c
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Radius.md)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = Ec.t.body.copy(color = c.textPrimary),
        cursorBrush = SolidColor(c.primary),
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .then(if (singleLine) Modifier else Modifier.heightIn(max = height))
            .clip(shape)
            .background(if (tinted) Color.Transparent else c.inputBg)
            .border(if (focused && !isError) 2.dp else 1.dp, fieldBorder(isError, focused, tinted), shape) // focus: 1px border + 1px ring on the site
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                Modifier.padding(horizontal = Space.s4, vertical = if (singleLine) 0.dp else Space.s3),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = if (singleLine) height - 2.dp else 0.dp),
                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = Ec.t.body, color = c.textMuted, maxLines = if (singleLine) 1 else 3, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
                if (trailing != null) trailing()
            }
        },
    )
}

/** Error line under a field (.form-error). */
@Composable
fun EcFieldError(text: String) {
    Row(
        Modifier.padding(top = Space.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s1),
    ) {
        EcIcon(EcIcons.HelpCircle, Ec.c.danger, 13.dp)
        Text(text, style = Ec.t.caption, color = Ec.c.danger)
    }
}

/** Label above a control (.form-label) with the control and optional error below (.form-group). */
@Composable
fun EcFormGroup(
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Text(label, style = Ec.t.label, color = Ec.c.textPrimary)
        content()
        if (error != null) EcFieldError(error)
    }
}

/** Custom select (select-enhancer.js): a trigger that looks like a field and a listbox below it. */
@Composable
fun EcDropdown(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    tinted: Boolean = false,
) {
    val c = Ec.c
    var open by remember { mutableStateOf(false) }
    var widthPx by remember { mutableStateOf(0) }
    val shape = RoundedCornerShape(Radius.md)
    val label = options.firstOrNull { it.first == selected }?.second ?: placeholder
    val rotation by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, tween(160), label = "chev")

    Box(modifier.fillMaxWidth().onSizeChanged { widthPx = it.width }) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(shape)
                .background(if (tinted) Color.Transparent else c.inputBg)
                .border(if (open) 2.dp else 1.dp, fieldBorder(false, open, tinted), shape)
                .clickable(role = Role.DropdownList) { open = true }
                .padding(horizontal = Space.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            Text(
                label,
                style = Ec.t.body,
                color = if (selected.isEmpty()) c.textMuted else c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            EcIcon(EcIcons.ChevronDown, c.textSecondary, 18.dp, Modifier.rotate(rotation))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(with(LocalDensity.current) { widthPx.toDp() }).heightIn(max = 260.dp),
            containerColor = c.surface,
            shape = shape,
            border = BorderStroke(1.dp, c.border),
        ) {
            options.forEach { (value, text) ->
                val on = value == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            text,
                            style = if (on) Ec.t.bodyStrong else Ec.t.body,
                            color = if (on) c.primary else c.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = { onSelect(value); open = false },
                    modifier = Modifier.background(if (on) c.primaryWash else Color.Transparent),
                )
            }
        }
    }
}

/** .toggle: 44 x 24 track, 18dp thumb. */
@Composable
fun EcToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Ec.c
    val track by animateColorAsState(if (checked) c.primary else c.borderStrong, tween(150), label = "track")
    val thumbX by animateDpAsState(if (checked) 23.dp else 3.dp, tween(150), label = "thumb")
    Box(
        modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(CircleShape)
            .background(track)
            .clickable(role = Role.Switch) { onChange(!checked) },
    ) {
        Box(
            Modifier
                .offset(x = thumbX, y = 3.dp)
                .size(18.dp)
                .shadowSm(Ec.c.isDark, 9.dp) // .toggle__thumb: --shadow-sm
                .background(Color.White, CircleShape),
        )
    }
}

/** .spinner: 3dp ring with a primary arc. */
@Composable
fun EcSpinner(size: Dp = 40.dp, color: Color = Ec.c.primary, track: Color = Ec.c.border, stroke: Dp = 3.dp) {
    CircularProgressIndicator(
        modifier = Modifier.size(size),
        color = color,
        trackColor = track,
        strokeWidth = stroke,
    )
}
