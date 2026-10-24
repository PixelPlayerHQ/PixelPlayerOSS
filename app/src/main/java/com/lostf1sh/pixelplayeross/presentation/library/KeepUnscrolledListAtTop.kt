package com.lostf1sh.pixelplayeross.presentation.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

/**
 * Holds an unscrolled list at its top when [rows] change.
 *
 * A keyed lazy list keeps its first visible row on screen when the data changes, even from the
 * top. Folder songs the library cache does not hold yet (right after launch or a memory trim)
 * open as title-only stubs and re-sort once their details load, so without this a folder longer
 * than the screen would open scrolled to wherever its first song by title landed. A list the user
 * has scrolled, or one in the middle of a scroll (such as a locate animation), keeps following its
 * first visible row.
 */
@Composable
internal fun KeepUnscrolledListAtTop(listState: LazyListState, rows: List<*>) {
    val shown = remember(listState) { ShownRows(rows) }
    // Runs after the new rows are composed and before the list measures them.
    SideEffect {
        if (shown.rows == rows) return@SideEffect
        shown.rows = rows
        val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        if (isAtTop && !listState.isScrollInProgress) {
            listState.requestScrollToItem(0)
        }
    }
}

private class ShownRows(var rows: List<*>)
