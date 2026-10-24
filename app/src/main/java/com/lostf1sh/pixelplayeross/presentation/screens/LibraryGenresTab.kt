@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.lostf1sh.pixelplayeross.data.model.Genre
import com.lostf1sh.pixelplayeross.data.model.LibraryTabId
import com.lostf1sh.pixelplayeross.data.model.StorageFilter
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.screens.search.components.GenreCategoriesGrid
import com.lostf1sh.pixelplayeross.presentation.viewmodel.LibraryGenresState
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel

/**
 * The Library's Genres tab: the same genre tiles as Search, with song counts, in the sort chosen
 * from the library sort sheet. [state] is null until the genres have loaded.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryGenresTab(
    state: LibraryGenresState?,
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    onGenreClick: (Genre) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    isSelectionMode: Boolean = false,
    selectedGenreIds: Set<String> = emptySet(),
    onGenreLongPress: (Genre) -> Unit = {},
    onGenreSelectionToggle: (Genre) -> Unit = {},
    getSelectionIndex: (String) -> Int? = { null }
) {
    val gridState = rememberLazyGridState()

    // A grid keeps the first visible tile in place when the order changes, which after a flip
    // (A-Z to Z-A) is the far end of the list. The state carries the sort it was built with, so
    // this runs once the re-sorted list has arrived.
    val sortKey = state?.sortOption?.storageKey
    var handledSortKey by remember { mutableStateOf(sortKey) }
    LaunchedEffect(sortKey) {
        if (sortKey == null || sortKey == handledSortKey) return@LaunchedEffect
        val isFirstLoad = handledSortKey == null
        handledSortKey = sortKey
        if (!isFirstLoad) gridState.scrollToItem(0)
    }

    when {
        state == null -> Box(modifier = Modifier.fillMaxSize())

        state.genres.isEmpty() -> LibraryExpressiveEmptyState(
            tabId = LibraryTabId.GENRES,
            storageFilter = StorageFilter.ALL,
            bottomBarHeight = bottomBarHeight
        )

        else -> {
            val pullToRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                state = pullToRefreshState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullToRefreshState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            ) {
                GenreCategoriesGrid(
                    genres = state.genres,
                    onGenreClick = onGenreClick,
                    playerViewModel = playerViewModel,
                    isSelectionMode = isSelectionMode,
                    selectedGenreIds = selectedGenreIds,
                    onGenreLongPress = onGenreLongPress,
                    onGenreSelectionToggle = onGenreSelectionToggle,
                    getSelectionIndex = getSelectionIndex,
                    showHeader = false,
                    showSongCount = true,
                    horizontalPadding = 12.dp,
                    contentPadding = PaddingValues(
                        bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap
                    ),
                    gridState = gridState
                )
            }
        }
    }
}
