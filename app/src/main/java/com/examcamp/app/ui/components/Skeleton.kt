package com.examcamp.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.examcamp.app.ui.theme.Ec
import com.examcamp.app.ui.theme.Radius
import com.examcamp.app.ui.theme.Space

/** One grey placeholder bar that pulses while content loads. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    widthFraction: Float = 1f,
    radius: Dp = Radius.md,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(
        modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(radius))
            .alpha(alpha)
            .background(Ec.c.border),
    )
}

/** A card-shaped placeholder: a title bar and a few text lines. */
@Composable
fun SkeletonCard(lines: Int = 3, modifier: Modifier = Modifier) {
    EcCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            SkeletonBlock(height = 18.dp, widthFraction = 0.55f)
            repeat(lines) { i ->
                SkeletonBlock(height = 12.dp, widthFraction = if (i == lines - 1) 0.4f else 0.9f)
            }
        }
    }
}

/** Live Exam page while its first load (or the saved copy) is still on its way. */
@Composable
fun LiveSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
        SkeletonCard(lines = 3)
        SkeletonCard(lines = 2)
        SkeletonBlock(height = 44.dp, radius = Radius.lg)
        SkeletonCard(lines = 4)
    }
}
