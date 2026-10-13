package com.grillpoet.docy.app.ui.selection

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 在选择模式下，长按后拖动，从起点行刷到终点行。
 * 手指不能拖出可视区外（不做自动滚动）。
 */
fun Modifier.slideMultiSelect(
    listState: LazyListState,
    enabled: Boolean,
    onRange: (Int, Int) -> Unit
): Modifier {
    if (!enabled) return this
    return this.pointerInput(listState, enabled) {
        var anchor: Int? = null

        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                anchor = itemAt(listState, offset.y.toInt())
            },
            onDrag = { change, _ ->
                val a = anchor ?: return@detectDragGesturesAfterLongPress
                val t = itemAt(listState, change.position.y.toInt())
                if (t != null && t != a) {
                    onRange(minOf(a, t), maxOf(a, t))
                }
                change.consume()
            },
            onDragEnd = { anchor = null },
            onDragCancel = { anchor = null }
        )
    }
}

private fun itemAt(listState: LazyListState, y: Int): Int? =
    listState.layoutInfo.visibleItemsInfo
        .firstOrNull { y in it.offset..(it.offset + it.size) }
        ?.index