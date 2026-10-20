package com.examcamp.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.examcamp.app.ui.theme.Ec
import com.examcamp.app.ui.theme.Radius
import com.examcamp.app.ui.theme.Space

/** .card: flat surface, hairline border, 8dp radius. */
@Composable
fun EcCard(
    modifier: Modifier = Modifier,
    background: Color = Ec.c.surface,
    borderColor: Color = Ec.c.border,
    padding: Dp = Space.s5, // .card padding on phones: 20px (24px above 640)
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.lg)
    Column(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .padding(padding),
        content = content,
    )
}

enum class BtnStyle { Primary, Secondary, Soft, Danger, Ghost }

/** .btn / .btn-primary / .btn-secondary / .btn-sm. */
@Composable
fun EcButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BtnStyle = BtnStyle.Primary,
    small: Boolean = false,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = Ec.c
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val shape = RoundedCornerShape(Radius.sm)
    val look: Triple<Color, Color, Color?> = when (style) {
        // Website: .btn-primary is always --wf-blue with white text, in the dark theme too (its :active is #0047b3).
        BtnStyle.Primary -> Triple(if (pressed) Color(0xFF0047B3) else Color(0xFF146EF5), Color.White, null)
        BtnStyle.Secondary -> Triple(
            if (pressed) c.textPrimary else Color.Transparent,
            if (pressed) c.bg else c.textPrimary,
            c.textPrimary,
        )
        BtnStyle.Soft -> Triple(if (pressed) c.primaryBorder else c.primaryLight, c.primary, c.primaryBorder)
        BtnStyle.Danger -> Triple(if (pressed) Color(0xFFC8132B) else c.danger, Color.White, null) // site: .btn-danger:hover #c8132b
        BtnStyle.Ghost -> Triple(if (pressed) c.hoverBg else Color.Transparent, c.textSecondary, null)
    }
    val bg = look.first
    val fg = look.second
    val borderColor = look.third
    // .btn:active { transform: scale(0.98) }
    val press by animateFloatAsState(if (pressed && enabled) 0.98f else 1f, tween(120), label = "btnPress")
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = press; scaleY = press }
            .heightIn(min = if (small) 36.dp else 44.dp) // .btn { height: 44px }, .btn-sm 36px
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(bg)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .clickable(enabled = enabled, interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) Space.s4 else Space.s5),
        horizontalArrangement = Arrangement.spacedBy(Space.s2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading()
        Text(text, style = if (small) Ec.t.smallStrong else Ec.t.button, color = fg)
    }
}

@Composable
fun EcIcon(icon: ImageVector, tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** .icon-btn: square, primary-border outline, primary icon. */
@Composable
fun EcIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val c = Ec.c
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val shape = RoundedCornerShape(Radius.md)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (pressed) c.primaryLight else c.surface)
            .border(1.dp, if (pressed) c.primary else c.primaryBorder, shape)
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = c.primary, modifier = Modifier.size(size * 0.5f))
    }
}

/** .segmented: pill group where the pressed option gets the page background and a hairline shadow. */
@Composable
fun EcSegmented(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    compact: Boolean = false,
) {
    val c = Ec.c
    val outer = RoundedCornerShape(Radius.md)
    Row(
        modifier = modifier
            .clip(outer)
            .background(c.surfaceAlt)
            .border(1.dp, c.primaryBorder, outer)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            val shape = RoundedCornerShape(Radius.sm)
            Box(
                modifier = (if (fill) Modifier.weight(1f) else Modifier)
                    .heightIn(min = if (compact) 30.dp else 36.dp)
                    .then(if (on) Modifier.shadowSm(c.isDark, Radius.sm) else Modifier)
                    .clip(shape)
                    .background(if (on) c.bg else Color.Transparent)
                    .clickable(role = Role.RadioButton, onClick = { onSelect(value) })
                    .padding(horizontal = if (compact) Space.s2 else Space.s3),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Ec.t.label, color = if (on) c.textPrimary else c.textSecondary)
            }
        }
    }
}

@Composable
fun FullWidthColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), content = content)
}
