package com.examcamp.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.examcamp.app.i18n.t
import com.examcamp.app.ui.theme.Ec
import com.examcamp.app.ui.theme.Space

/**
 * Slim strip at the bottom of the app while there is no network. It sits at the bottom, not the top,
 * so it never fights the status-bar inset that each screen header already handles.
 */
@Composable
fun OfflineBanner(online: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = !online,
        modifier = modifier,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Ec.c.warningLight)
                .padding(horizontal = Space.s4, vertical = Space.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Ec.c.warning))
            Text(t("net.offline"), style = Ec.t.small, color = Ec.c.textPrimary)
        }
    }
}
