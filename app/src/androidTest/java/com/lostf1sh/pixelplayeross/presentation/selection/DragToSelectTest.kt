package com.lostf1sh.pixelplayeross.presentation.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.EnhancedSongListItem
import com.lostf1sh.pixelplayeross.presentation.viewmodel.MultiSelectionStateHolder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Holding a song and dragging selects every song the finger passes over, and the list stays
 * still until the finger lifts. Taps and plain swipes keep their usual meaning.
 */
@RunWith(AndroidJUnit4::class)
class DragToSelectTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val holder = MultiSelectionStateHolder()
    private val songs = List(40) { index ->
        Song.emptySong().copy(id = "song-$index", title = "Song $index", artist = "Artist", album = "Album")
    }
    /** The songs as the list shows them; tests reorder or update them mid-drag. */
    private var rows by mutableStateOf(songs)
    private lateinit var listState: LazyListState

    /**
     * Shows the songs in a list under [header], which gets whether selection mode is on, inside
     * [wrapper]. The whole screen is tagged [ROOT_TAG], so touches injected through it stay put
     * on screen when the list moves.
     */
    private fun setList(
        rootModifier: Modifier = Modifier,
        wrapper: @Composable (content: @Composable () -> Unit) -> Unit = { content -> content() },
        header: @Composable (isSelectionMode: Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                val selectedIds by holder.selectedSongIds.collectAsState()
                val isSelectionMode by holder.isSelectionMode.collectAsState()
                val dragSelectState = rememberDragToSelectState<Song>()
                listState = rememberLazyListState()
                wrapper {
                    Column(modifier = rootModifier.fillMaxSize().testTag(ROOT_TAG)) {
                        header(isSelectionMode)
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .testTag(LIST_TAG)
                                .dragToSelect(
                                    state = dragSelectState,
                                    listState = listState,
                                    itemAt = { index -> rows.getOrNull(index) },
                                    itemKey = Song::id,
                                    selectedKeys = { holder.selectedSongIds.value },
                                    onSelectionChange = holder::applyDragSelection,
                                ),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(rows, key = { it.id }) { song ->
                                EnhancedSongListItem(
                                    modifier = Modifier.testTag(song.id),
                                    song = song,
                                    isPlaying = false,
                                    isSelected = song.id in selectedIds,
                                    selectionIndex = holder.getSelectionIndex(song.id),
                                    isSelectionMode = isSelectionMode,
                                    onLongPress = {
                                        holder.toggleSelection(song)
                                        dragSelectState.onItemLongPressed(song)
                                    },
                                    onMoreOptionsClick = {},
                                    onClick = {},
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Row positions in the list's own coordinates; read them before a selection scales the rows.
    private fun rowCenterY(index: Int): Float {
        val list = composeRule.onNodeWithTag(LIST_TAG).fetchSemanticsNode().boundsInRoot
        val row = composeRule.onNodeWithTag(songs[index].id).fetchSemanticsNode().boundsInRoot
        return row.center.y - list.top
    }

    private fun gapBelowRowY(index: Int): Float {
        val list = composeRule.onNodeWithTag(LIST_TAG).fetchSemanticsNode().boundsInRoot
        val row = composeRule.onNodeWithTag(songs[index].id).fetchSemanticsNode().boundsInRoot
        val next = composeRule.onNodeWithTag(songs[index + 1].id).fetchSemanticsNode().boundsInRoot
        return (row.bottom + next.top) / 2f - list.top
    }

    private fun boundsInRoot(tag: String) = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun selectedIds() = holder.selectedSongs.value.map { it.id }

    @Test
    fun longPressThenDragSelectsTheRowsPassedOverInOrderWithoutScrolling() {
        setList()
        val x = composeRule.onNodeWithTag(LIST_TAG).fetchSemanticsNode().size.width / 2f
        val row0 = rowCenterY(0)
        val gap = gapBelowRowY(0)
        val row4 = rowCenterY(4)

        composeRule.onNodeWithTag(LIST_TAG).performTouchInput {
            down(Offset(x, row0))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            // A fast first move that lands between two rows must not lose the drag.
            moveTo(Offset(x, gap))
            moveTo(Offset(x, (gap + row4) / 2f))
            moveTo(Offset(x, row4))
            up()
        }

        composeRule.runOnIdle {
            assertThat(selectedIds()).containsExactly("song-0", "song-1", "song-2", "song-3", "song-4").inOrder()
            assertThat(listState.firstVisibleItemIndex).isEqualTo(0)
            assertThat(listState.firstVisibleItemScrollOffset).isEqualTo(0)
        }
    }

    @Test
    fun draggingBackGivesRowsBackTheirStateAndPlaceInTheOrder() {
        setList()
        val x = composeRule.onNodeWithTag(LIST_TAG).fetchSemanticsNode().size.width / 2f
        val rows = (0..3).map(::rowCenterY)
        composeRule.runOnIdle { (0..3).forEach { holder.toggleSelection(songs[it]) } }

        // Holding the selected song 0 deselects it, so the drag deselects too.
        composeRule.onNodeWithTag(LIST_TAG).performTouchInput {
            down(Offset(x, rows[0]))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveTo(Offset(x, rows[1]))
            moveTo(Offset(x, rows[2]))
        }
        composeRule.runOnIdle { assertThat(selectedIds()).containsExactly("song-3") }

        composeRule.onNodeWithTag(LIST_TAG).performTouchInput {
            moveTo(Offset(x, rows[1]))
            moveTo(Offset(x, rows[0]))
            up()
        }
        composeRule.runOnIdle {
            assertThat(selectedIds()).containsExactly("song-1", "song-2", "song-3").inOrder()
        }
    }

    @Test
    fun tapsAndPlainSwipesInSelectionModeDoNotDragSelect() {
        setList()
        composeRule.runOnIdle { holder.toggleSelection(songs[0]) }
        val x = composeRule.onNodeWithTag(LIST_TAG).fetchSemanticsNode().size.width / 2f
        val row2 = rowCenterY(2)
        val row8 = rowCenterY(8)

        composeRule.onNodeWithTag(songs[2].id).performTouchInput { click() }
        composeRule.runOnIdle { assertThat(selectedIds()).containsExactly("song-0", "song-2").inOrder() }

        composeRule.onNodeWithTag(LIST_TAG).performTouchInput {
            down(Offset(x, row8))
            moveTo(Offset(x, (row8 + row2) / 2f))
            moveTo(Offset(x, row2))
            up()
        }

        composeRule.runOnIdle {
            assertThat(selectedIds()).containsExactly("song-0", "song-2").inOrder()
            assertThat(listState.firstVisibleItemIndex).isGreaterThan(0)
        }
    }

    @Test
    fun aListThatMovesUnderTheStillFingerAsSelectionStartsDoesNotSelectTheRowItSlidUnder() {
        // Like Search's filter chips giving way to the shorter selection actions.
        setList { isSelectionMode ->
            Box(Modifier.fillMaxWidth().height(if (isSelectionMode) 40.dp else 160.dp))
        }
        val x = boundsInRoot(LIST_TAG).center.x
        val listTopBefore = boundsInRoot(LIST_TAG).top
        // The lower part of row 3, where a long press leaves the finger closest to the row below.
        val fingerY = boundsInRoot(songs[3].id).bottom - 4f
        var longPressTimeout = 0L

        composeRule.onNodeWithTag(ROOT_TAG).performTouchInput {
            longPressTimeout = viewConfiguration.longPressTimeoutMillis
            down(Offset(x, fingerY))
        }
        composeRule.mainClock.advanceTimeBy(longPressTimeout + 100)
        composeRule.runOnIdle { assertThat(selectedIds()).containsExactly("song-3") }
        // The header shrank: the list moved up, and the still finger is over a row below now.
        assertThat(boundsInRoot(LIST_TAG).top).isLessThan(listTopBefore)
        assertThat(boundsInRoot(songs[3].id).bottom).isLessThan(fingerY)

        composeRule.onNodeWithTag(ROOT_TAG).performTouchInput { moveBy(Offset(0f, 2f)) }
        composeRule.runOnIdle { assertThat(selectedIds()).containsExactly("song-3") }

        // A real drag then follows the rows where they are on screen now.
        val row6 = boundsInRoot(songs[6].id).center.y
        composeRule.onNodeWithTag(ROOT_TAG).performTouchInput {
            moveTo(Offset(x, row6))
            up()
        }
        composeRule.runOnIdle {
            assertThat(selectedIds()).containsExactly("song-3", "song-4", "song-5", "song-6").inOrder()
        }
    }

    @Test
    fun autoScrollCollapsesANestedScrollTopBarLikeAFingerDragDoes() {
        val density = composeRule.density
        val minHeaderHeightPx = with(density) { 60.dp.toPx() }
        var headerHeightPx by mutableFloatStateOf(with(density) { 200.dp.toPx() })
        val sources = mutableSetOf<NestedScrollSource>()
        var postFlings = 0
        // Collapses as the list scrolls towards its end, like genre detail's top bar.
        val connection = object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                sources += source
                if (available.y >= 0f) return Offset.Zero
                val newHeight = (headerHeightPx + available.y).coerceAtLeast(minHeaderHeightPx)
                val consumed = newHeight - headerHeightPx
                headerHeightPx = newHeight
                return Offset(0f, consumed)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                postFlings++
                return Velocity.Zero
            }
        }
        setList(rootModifier = Modifier.nestedScroll(connection)) {
            Box(Modifier.fillMaxWidth().height(with(density) { headerHeightPx.toDp() }))
        }
        val x = boundsInRoot(LIST_TAG).center.x
        val row0 = boundsInRoot(songs[0].id).center.y
        val bottomEdge = boundsInRoot(LIST_TAG).bottom - 8f

        composeRule.onNodeWithTag(ROOT_TAG).performTouchInput {
            down(Offset(x, row0))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveTo(Offset(x, bottomEdge))
            // Rest in the bottom edge zone while the list auto-scrolls.
            repeat(20) { step ->
                advanceEventTime(100)
                moveBy(Offset(0f, if (step % 2 == 0) 1f else -1f))
            }
            up()
        }

        composeRule.runOnIdle {
            assertThat(headerHeightPx).isEqualTo(minHeaderHeightPx)
            assertThat(sources).containsExactly(NestedScrollSource.UserInput)
            assertThat(listState.firstVisibleItemIndex).isGreaterThan(0)
            // The finger let go over the bottom row: everything from song 0 down to it is selected.
            val selected = selectedIds()
            assertThat(selected).isEqualTo(songs.take(selected.size).map { it.id })
            assertThat(selected.size).isGreaterThan(listState.firstVisibleItemIndex)
            // Lifting settles the top bar like a drag that ends without a fling.
            assertThat(postFlings).isEqualTo(1)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun autoScrollIntoTheTopOfTheListNeitherPullsNorTriggersPullToRefresh() {
        var refreshes = 0
        var maxPullFraction = 0f
        // Like the Library tabs, whose lists sit in a PullToRefreshBox.
        setList(
            wrapper = { content ->
                val pullState = rememberPullToRefreshState()
                LaunchedEffect(pullState) {
                    snapshotFlow { pullState.distanceFraction }.collect { maxPullFraction = maxOf(maxPullFraction, it) }
                }
                PullToRefreshBox(
                    isRefreshing = false,
                    onRefresh = { refreshes++ },
                    state = pullState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    content()
                }
            },
        )
        composeRule.onNodeWithTag(LIST_TAG).performScrollToIndex(3)
        val x = boundsInRoot(LIST_TAG).center.x
        val row5 = boundsInRoot(songs[5].id).center.y
        // Deep in the top edge zone, over the first visible row.
        val topEdge = boundsInRoot(LIST_TAG).top + 8f

        composeRule.onNodeWithTag(ROOT_TAG).performTouchInput {
            down(Offset(x, row5))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveTo(Offset(x, topEdge))
            // Rest in the top edge zone while the list auto-scrolls to its top, and well past the
            // time a pull of that speed would take to cross the refresh threshold.
            repeat(20) { step ->
                advanceEventTime(100)
                moveBy(Offset(0f, if (step % 2 == 0) 1f else -1f))
            }
            up()
        }

        composeRule.runOnIdle {
            assertThat(listState.firstVisibleItemIndex).isEqualTo(0)
            assertThat(listState.firstVisibleItemScrollOffset).isEqualTo(0)
            assertThat(selectedIds())
                .containsExactly("song-5", "song-4", "song-3", "song-2", "song-1", "song-0").inOrder()
            assertThat(maxPullFraction).isEqualTo(0f)
            assertThat(refreshes).isEqualTo(0)
        }
    }

    @Test
    fun rowsThatReorderUnderTheDragEndItAndKeepWhatItSelected() {
        setList()
        val x = boundsInRoot(LIST_TAG).center.x
        val rowsBefore = (0..3).map { boundsInRoot(songs[it].id).center.y }

        composeRule.onNodeWithTag(ROOT_TAG).performTouchInput {
            down(Offset(x, rowsBefore[0]))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveTo(Offset(x, rowsBefore[1]))
            moveTo(Offset(x, rowsBefore[3]))
        }
        composeRule.runOnIdle {
            assertThat(selectedIds()).containsExactly("song-0", "song-1", "song-2", "song-3").inOrder()
            // Like a folder song whose details load and sort it to the top, under the drag.
            rows = listOf(songs[5]) + songs.filterNot { it.id == "song-5" }
        }
        val firstVisibleAfterReorder = composeRule.runOnIdle { listState.firstVisibleItemIndex }
        val song7 = boundsInRoot("song-7").center.y
        val song1 = boundsInRoot("song-1").center.y

        // Without the rows the drag found, going on would select and restore the wrong songs.
        composeRule.onNodeWithTag(ROOT_TAG).performTouchInput {
            moveTo(Offset(x, song7))
            moveTo(Offset(x, song1))
            up()
        }

        composeRule.runOnIdle {
            assertThat(selectedIds()).containsExactly("song-0", "song-1", "song-2", "song-3").inOrder()
            // The finger held the list until it lifted.
            assertThat(listState.firstVisibleItemIndex).isEqualTo(firstVisibleAfterReorder)
        }
    }

    @Test
    fun rowsUpdatedInPlaceUnderTheDragKeepItGoing() {
        setList()
        val x = boundsInRoot(LIST_TAG).center.x
        val rowsBefore = (0..6).map(::rowCenterY)

        composeRule.onNodeWithTag(LIST_TAG).performTouchInput {
            down(Offset(x, rowsBefore[0]))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveTo(Offset(x, rowsBefore[1]))
            moveTo(Offset(x, rowsBefore[3]))
        }
        // Like folder songs whose details load without changing their order.
        composeRule.runOnIdle { rows = songs.map { it.copy(title = "${it.title} (loaded)") } }

        composeRule.onNodeWithTag(LIST_TAG).performTouchInput {
            moveTo(Offset(x, rowsBefore[6]))
            moveTo(Offset(x, rowsBefore[5]))
            up()
        }

        composeRule.runOnIdle {
            assertThat(selectedIds())
                .containsExactly("song-0", "song-1", "song-2", "song-3", "song-4", "song-5").inOrder()
        }
    }

    @Test
    fun clearingTheSelectionMidDragEndsTheDragInsteadOfSelectingAgain() {
        setList()
        val x = boundsInRoot(LIST_TAG).center.x
        val rowsBefore = (0..6).map(::rowCenterY)

        composeRule.onNodeWithTag(LIST_TAG).performTouchInput {
            down(Offset(x, rowsBefore[0]))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveTo(Offset(x, rowsBefore[1]))
            moveTo(Offset(x, rowsBefore[3]))
        }
        composeRule.runOnIdle {
            assertThat(selectedIds()).containsExactly("song-0", "song-1", "song-2", "song-3").inOrder()
            // What the screens' back handlers do on Back in selection mode.
            holder.clearSelection()
        }

        composeRule.onNodeWithTag(LIST_TAG).performTouchInput {
            moveTo(Offset(x, rowsBefore[5]))
            moveTo(Offset(x, rowsBefore[6]))
        }
        composeRule.runOnIdle {
            assertThat(selectedIds()).isEmpty()
            assertThat(holder.isSelectionMode.value).isFalse()
        }

        // The finger still holds the list: dragging it up does not scroll it.
        composeRule.onNodeWithTag(LIST_TAG).performTouchInput {
            moveTo(Offset(x, rowsBefore[1]))
            up()
        }
        composeRule.runOnIdle {
            assertThat(selectedIds()).isEmpty()
            assertThat(listState.firstVisibleItemIndex).isEqualTo(0)
            assertThat(listState.firstVisibleItemScrollOffset).isEqualTo(0)
        }
    }

    @Test
    fun clearingTheSelectionMidDragKeepsADeselectingDragFromGivingRowsBack() {
        setList()
        val x = boundsInRoot(LIST_TAG).center.x
        val rows = (0..3).map(::rowCenterY)
        composeRule.runOnIdle { (0..3).forEach { holder.toggleSelection(songs[it]) } }

        // Holding the selected song 0 deselects it, so the drag deselects too.
        composeRule.onNodeWithTag(LIST_TAG).performTouchInput {
            down(Offset(x, rows[0]))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveTo(Offset(x, rows[1]))
            moveTo(Offset(x, rows[2]))
        }
        composeRule.runOnIdle {
            assertThat(selectedIds()).containsExactly("song-3")
            holder.clearSelection()
        }

        // Dragging back would otherwise give songs 1 and 2 back their selection.
        composeRule.onNodeWithTag(LIST_TAG).performTouchInput {
            moveTo(Offset(x, rows[1]))
            moveTo(Offset(x, rows[0]))
            up()
        }

        composeRule.runOnIdle {
            assertThat(selectedIds()).isEmpty()
            assertThat(holder.isSelectionMode.value).isFalse()
        }
    }

    @Test
    fun clearingTheSelectionMidAutoScrollStopsTheScrollAndItsSelecting() {
        setList()
        val x = boundsInRoot(LIST_TAG).center.x
        val row0 = boundsInRoot(songs[0].id).center.y
        val bottomEdge = boundsInRoot(LIST_TAG).bottom - 8f
        // Auto-scroll keeps asking for frames while the finger rests in an edge zone.
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag(ROOT_TAG).performTouchInput {
            down(Offset(x, row0))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveTo(Offset(x, bottomEdge))
            repeat(3) { step ->
                advanceEventTime(100)
                moveBy(Offset(0f, if (step % 2 == 0) 1f else -1f))
            }
        }
        val scrollAtClear = composeRule.runOnIdle {
            assertThat(listState.firstVisibleItemIndex).isGreaterThan(0)
            assertThat(selectedIds().size).isGreaterThan(1)
            holder.clearSelection()
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }

        // The finger rests in the bottom edge zone, where auto-scroll would go on selecting rows.
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.runOnIdle {
            assertThat(selectedIds()).isEmpty()
            assertThat(listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset)
                .isEqualTo(scrollAtClear)
        }
        composeRule.onNodeWithTag(ROOT_TAG).performTouchInput { up() }
        composeRule.mainClock.autoAdvance = true
        composeRule.runOnIdle {
            assertThat(selectedIds()).isEmpty()
            assertThat(holder.isSelectionMode.value).isFalse()
        }
    }

    private companion object {
        const val ROOT_TAG = "drag_select_root"
        const val LIST_TAG = "drag_select_list"
    }
}
