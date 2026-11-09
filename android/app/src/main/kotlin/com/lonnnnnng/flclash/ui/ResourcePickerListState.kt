package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.first

/** 作者 long：对应Flutter SelectionSheet首次显现当前项；目录再长也能确认原选择，后续滚动与筛选由用户控制。 */
@Composable
internal fun <K : Any> rememberResourcePickerListState(keys: List<K>, selectedKey: K?): LazyListState {
    val state = rememberLazyListState()
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(keys, selectedKey) {
        val index = if (selectedKey == null) -1 else keys.indexOf(selectedKey)
        if (revealed || index < 0) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.totalItemsCount }.first { it == keys.size }
        val layout = state.layoutInfo
        val visible = layout.visibleItemsInfo.firstOrNull { it.index == index }
        if (visible == null || visible.offset < layout.viewportStartOffset ||
            visible.offset + visible.size > layout.viewportEndOffset) {
            state.scrollToItem(index)
            // 作者 long：使用实际行高和当前键盘后的视口居中，不能假设固定高度或用定时延迟猜布局完成。
            val item = snapshotFlow { state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } }
                .first { it != null }!!
            val measured = state.layoutInfo
            val center = (measured.viewportStartOffset + measured.viewportEndOffset - item.size) / 2f
            state.scrollBy(item.offset - center)
        }
        revealed = true
    }
    return state
}
