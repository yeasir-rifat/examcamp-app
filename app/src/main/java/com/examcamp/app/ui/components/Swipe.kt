package com.examcamp.app.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Swipe left or right on a tab body to move to the next or previous tab. Vertical scrolling is
 * not touched: the gesture only starts once the finger moves sideways.
 */
fun Modifier.swipeTabs(selected: Int, count: Int, onSelect: (Int) -> Unit): Modifier =
    pointerInput(selected, count) {
        val threshold = 72.dp.toPx()
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                if (total <= -threshold && selected < count - 1) onSelect(selected + 1)
                else if (total >= threshold && selected > 0) onSelect(selected - 1)
                total = 0f
            },
            onDragCancel = { total = 0f },
            onHorizontalDrag = { _, delta -> total += delta },
        )
    }
