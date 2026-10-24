package com.lostf1sh.pixelplayeross.presentation.selection

import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScrollModifierNode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputEventHandler
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

private const val LogTag = "DragToSelect"

/**
 * Edge zones of the visible list in which a drag-select auto-scrolls. Kept below a song row's
 * height so the last row above the mini player can still be selected without scrolling past it.
 */
private val AutoScrollEdgeZone = 56.dp

/** Auto-scroll speed per second with the finger at the very edge of the visible list. */
private val MaxAutoScrollSpeed = 1000.dp

/**
 * Links a list's rows to its [dragToSelect] modifier. A row calls [onItemLongPressed] with its
 * item from its long press, after toggling itself, to let the finger that is still down go on
 * selecting rows.
 */
internal class DragToSelectState<T : Any> {
    internal var isPointerDown = false
    internal var longPressedItem: T? = null

    /**
     * Arms drag-to-select for the finger that is holding the row showing [item]. A long press that
     * does not come from a finger on the list (an accessibility action, or a tap in selection
     * mode, which is reported on release) is ignored.
     */
    fun onItemLongPressed(item: T) {
        if (isPointerDown) longPressedItem = item
    }
}

@Composable
internal fun <T : Any> rememberDragToSelectState(): DragToSelectState<T> = remember { DragToSelectState() }

/**
 * Drag-to-select for a [LazyColumn][androidx.compose.foundation.lazy.LazyColumn] of selectable
 * rows. Once a row's long press arms [state], the list stops scrolling (and a parent pager stops
 * swiping) until the finger lifts; every row the finger is dragged over takes the long-pressed
 * row's new state, and the list auto-scrolls while the finger rests near its top or bottom edge.
 *
 * The drag starts from the long-pressed row even if the list has moved under the finger since,
 * and only follows the finger once it has moved by more than the touch slop. Auto-scroll offers
 * each step to nested-scroll parents before the list scrolls, like a finger dragging the list,
 * so a collapsing top bar collapses with it; it never passes on what the list cannot scroll, so
 * it cannot pull a parent's pull-to-refresh.
 *
 * If the rows the drag has reached change order or shift while the finger is down (a list
 * re-sorting as its items load), or the selection changes other than through the drag (Back
 * clearing it), the drag ends there and leaves the selection as it is; the finger still holds the
 * list until it lifts.
 *
 * Append it last to the list's modifier chain so pointer positions line up with the list's items.
 * The list must not use `reverseLayout`.
 *
 * @param itemAt The selectable item at a list index, or null for rows that cannot be selected
 *   (headers, folders) or are not loaded yet.
 * @param itemKey A stable identity for an item, such as a song's id, that an updated copy of the
 *   item keeps. It tells whether a row still shows the item the drag found there.
 * @param selectedKeys The [itemKey]s of the items selected now, such as the selection holder's
 *   current set of song ids. Read it from the holder itself, not from composed state, which lags a
 *   frame behind the long press. Once it no longer equals the set the drag's last step left, the
 *   drag ends.
 * @param onSelectionChange Applies one drag step; songs in `select` are in drag order.
 * @param topInset Height of what overlays the top of the list's on-screen part, such as a
 *   collapsing top bar.
 * @param bottomInset Height of what overlays the bottom of the list's on-screen part, such as
 *   the navigation bar and mini player.
 */
internal fun <T : Any> Modifier.dragToSelect(
    state: DragToSelectState<T>,
    listState: LazyListState,
    itemAt: (index: Int) -> T?,
    itemKey: (T) -> Any,
    selectedKeys: () -> Set<Any>,
    onSelectionChange: (select: List<T>, deselect: List<T>) -> Unit,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
): Modifier = this then DragToSelectElement(
    state = state,
    listState = listState,
    itemAt = itemAt,
    itemKey = itemKey,
    selectedKeys = selectedKeys,
    onSelectionChange = onSelectionChange,
    topInset = topInset,
    bottomInset = bottomInset,
)

private data class DragToSelectElement<T : Any>(
    val state: DragToSelectState<T>,
    val listState: LazyListState,
    val itemAt: (Int) -> T?,
    val itemKey: (T) -> Any,
    val selectedKeys: () -> Set<Any>,
    val onSelectionChange: (List<T>, List<T>) -> Unit,
    val topInset: Dp,
    val bottomInset: Dp,
) : ModifierNodeElement<DragToSelectNode<T>>() {
    override fun create() = DragToSelectNode(
        state = state,
        listState = listState,
        itemAt = itemAt,
        itemKey = itemKey,
        selectedKeys = selectedKeys,
        onSelectionChange = onSelectionChange,
        topInset = topInset,
        bottomInset = bottomInset,
    )

    override fun update(node: DragToSelectNode<T>) {
        node.update(state, listState, itemAt, itemKey, selectedKeys, onSelectionChange, topInset, bottomInset)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "dragToSelect"
        properties["topInset"] = topInset
        properties["bottomInset"] = bottomInset
    }
}

/** Takes no part in nested scrolling itself; the node only dispatches through it. */
private object PassThroughNestedScrollConnection : NestedScrollConnection

private class DragToSelectNode<T : Any>(
    private var state: DragToSelectState<T>,
    private var listState: LazyListState,
    private var itemAt: (Int) -> T?,
    private var itemKey: (T) -> Any,
    private var selectedKeys: () -> Set<Any>,
    private var onSelectionChange: (List<T>, List<T>) -> Unit,
    private var topInset: Dp,
    private var bottomInset: Dp,
) : DelegatingNode(), CompositionLocalConsumerModifierNode {

    // Keyed on nothing that changes with selection: entering selection mode must not restart
    // the gesture the long press is part of.
    private val pointerInputNode = delegate(
        SuspendingPointerInputModifierNode(PointerInputEventHandler { detectDragToSelect() })
    )

    // Auto-scroll goes to the list's nested-scroll parents the way a finger drag on the list does.
    private val nestedScrollDispatcher = NestedScrollDispatcher()

    init {
        delegate(nestedScrollModifierNode(PassThroughNestedScrollConnection, nestedScrollDispatcher))
    }

    private val isSelected: (T) -> Boolean = { item -> itemKey(item) in selectedKeys() }

    private var tracker: DragSelectionTracker<T>? = null
    private var rangeGate: DragRangeGate? = null
    private var edgeGate: AutoScrollEdgeGate? = null

    /** [selectedKeys] as the active drag last left it, by its long press or its last step. */
    private var selectionLeftByDrag: Set<Any>? = null

    /**
     * The finger in window coordinates. Positions in the list's own coordinates go stale when the
     * list moves on screen (a top bar collapsing above it) while the finger rests, so they are
     * derived from this whenever they are needed.
     */
    private var pointerInWindow = Offset.Zero

    /** The finger in the list's coordinates as of the last [updateGeometry]. */
    private var pointerY = 0f
    private var bandTop = 0f
    private var bandBottom = 0f
    private var autoScrollVelocity = 0f
    private var autoScrollJob: Job? = null
    private var hasDispatchedNestedScroll = false

    fun update(
        state: DragToSelectState<T>,
        listState: LazyListState,
        itemAt: (Int) -> T?,
        itemKey: (T) -> Any,
        selectedKeys: () -> Set<Any>,
        onSelectionChange: (List<T>, List<T>) -> Unit,
        topInset: Dp,
        bottomInset: Dp,
    ) {
        // New rows are checked against the active drag on its next step (see selectUnderPointer).
        this.itemAt = itemAt
        this.itemKey = itemKey
        this.selectedKeys = selectedKeys
        this.onSelectionChange = onSelectionChange
        this.topInset = topInset
        this.bottomInset = bottomInset
        if (this.state !== state || this.listState !== listState) {
            this.state = state
            this.listState = listState
            pointerInputNode.resetPointerInputHandler()
        }
    }

    private suspend fun PointerInputScope.detectDragToSelect() = coroutineScope {
        val edgeZone = AutoScrollEdgeZone.toPx()
        val maxVelocity = MaxAutoScrollSpeed.toPx()
        val touchSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            // The Initial pass runs before the rows, the list's own scrolling and any parent
            // pager, so consuming there once armed keeps all of them still until the finger lifts.
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val gestureState = state
            gestureState.isPointerDown = true
            gestureState.longPressedItem = null
            pointerInWindow = toWindow(down.position)
            // Once a drag starts, the finger holds the list until it lifts, even if the drag ends
            // early because the rows moved under it.
            var holdsList = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) break
                    if (!holdsList) {
                        // The long press fired between events, with the finger still at its last
                        // position; this event may already have moved it off the row.
                        gestureState.longPressedItem?.let { item ->
                            gestureState.longPressedItem = null
                            holdsList = startSelection(item, touchSlop)
                        }
                    }
                    pointerInWindow = toWindow(change.position)
                    if (!holdsList) continue
                    event.changes.forEach { it.consume() }
                    if (tracker == null) continue
                    rangeGate?.onPointerMoved(pointerInWindow.y)
                    updateGeometry()
                    selectUnderPointer()
                    updateAutoScroll(edgeZone, maxVelocity)
                }
            } finally {
                autoScrollVelocity = 0f
                autoScrollJob?.cancel()
                autoScrollJob = null
                if (hasDispatchedNestedScroll) {
                    hasDispatchedNestedScroll = false
                    launch { settleNestedScroll() }
                }
                tracker?.let { Timber.tag(LogTag).d("Drag-select ended on row %d", it.currentIndex) }
                tracker = null
                selectionLeftByDrag = null
                rangeGate = null
                edgeGate = null
                gestureState.isPointerDown = false
                gestureState.longPressedItem = null
            }
        }
    }

    private fun toWindow(position: Offset): Offset = requireLayoutCoordinates().localToWindow(position)

    /** Starts a drag from the row showing [item]; returns whether it started. */
    private fun startSelection(item: T, touchSlop: Float): Boolean {
        updateGeometry()
        val layoutInfo = listState.layoutInfo
        val anchor = anchorIndexOf(
            item = item,
            y = pointerY,
            viewportStartOffset = layoutInfo.viewportStartOffset,
            visibleItems = layoutInfo.visibleItemsInfo,
            itemAt = itemAt,
            itemKey = itemKey,
        )
        if (anchor == null) {
            Timber.tag(LogTag).w("Drag-select not started: the long-pressed row is not on screen")
            return false
        }
        // The long press has already toggled the anchor, so its state now is the drag's target.
        val selecting = isSelected(item)
        tracker = DragSelectionTracker(anchor, itemKey(item), selecting, itemKey)
        selectionLeftByDrag = selectedKeys()
        rangeGate = DragRangeGate(startY = pointerInWindow.y, touchSlop = touchSlop)
        edgeGate = AutoScrollEdgeGate(startY = pointerInWindow.y, touchSlop = touchSlop)
        Timber.tag(LogTag).d("Drag-select started on row %d, selecting=%b", anchor, selecting)
        return true
    }

    /**
     * Ends the drag before the finger lifts, leaving the selection as it is. The finger goes on
     * holding the list, and with no edge gate left auto-scroll winds down on its next step.
     */
    private fun endDragEarly(tracker: DragSelectionTracker<T>, reason: String) {
        Timber.tag(LogTag).d("Drag-select ended on row %d: %s", tracker.currentIndex, reason)
        this.tracker = null
        selectionLeftByDrag = null
        rangeGate = null
        edgeGate = null
        autoScrollVelocity = 0f
    }

    /**
     * Ends the drag if something other than the drag has changed the selection since its last
     * step, such as Back clearing it, and returns whether it did. The drag gives the rows it leaves
     * back the state they had before it reached them, which no longer holds then: going on would
     * select songs again and bring selection mode back.
     */
    private fun endDragIfSelectionChangedElsewhere(tracker: DragSelectionTracker<T>): Boolean {
        if (selectedKeys() == selectionLeftByDrag) return false
        endDragEarly(tracker, "the selection changed outside it")
        return true
    }

    /**
     * Extends the drag range to the row under the finger, limited to the rows on screen, once
     * the finger has moved off the long-pressed row's position or auto-scroll has moved the rows.
     */
    private fun selectUnderPointer() {
        val tracker = tracker ?: return
        if (endDragIfSelectionChangedElsewhere(tracker)) return
        if (rangeGate?.isOpen != true) return
        // The range is kept by row index: once its rows have moved (a list re-sorting as its
        // items load), going on would select and restore songs the finger never passed over.
        if (!tracker.isInPlace(itemAt)) {
            endDragEarly(tracker, "its rows moved")
            return
        }
        val y = if (bandBottom > bandTop) pointerY.coerceIn(bandTop, bandBottom - 1f) else pointerY
        val layoutInfo = listState.layoutInfo
        val index = itemIndexAtY(y, layoutInfo.viewportStartOffset, layoutInfo.visibleItemsInfo)
            ?: tracker.currentIndex
        val change = tracker.moveTo(index, itemAt, isSelected)
        if (change.isEmpty) return
        onSelectionChange(change.select, change.deselect)
        selectionLeftByDrag = selectedKeys()
        currentValueOf(LocalHapticFeedback).performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    /**
     * Maps the finger into the list's current layout, and finds the part of the list that is on
     * screen and not covered by its insets.
     */
    private fun updateGeometry() {
        if (!isAttached) return
        val coordinates = requireLayoutCoordinates()
        val density = requireDensity()
        pointerY = coordinates.windowToLocal(pointerInWindow).y
        var top = 0f
        var bottom = coordinates.size.height.toFloat()
        // Genre detail pushes its list down while the header is expanded, so the bottom of the
        // list is off screen; its overlays are laid out against the screen edge instead.
        val onScreen = coordinates.boundsInWindow()
        if (!onScreen.isEmpty) {
            top = maxOf(top, coordinates.windowToLocal(onScreen.topLeft).y)
            bottom = minOf(bottom, coordinates.windowToLocal(onScreen.bottomLeft).y)
        }
        bandTop = top + with(density) { topInset.toPx() }
        bandBottom = bottom - with(density) { bottomInset.toPx() }
    }

    private fun currentAutoScrollVelocity(edgeZone: Float, maxVelocity: Float): Float {
        val gate = edgeGate ?: return 0f
        val velocity = dragSelectAutoScrollVelocity(pointerY, bandTop, bandBottom, edgeZone, maxVelocity)
        return gate.filter(velocity, pointerInWindow.y)
    }

    private fun CoroutineScope.updateAutoScroll(edgeZone: Float, maxVelocity: Float) {
        autoScrollVelocity = currentAutoScrollVelocity(edgeZone, maxVelocity)
        if (autoScrollVelocity != 0f && autoScrollJob?.isActive != true) {
            autoScrollJob = launch { autoScroll(edgeZone, maxVelocity) }
        }
    }

    /** Scrolls while the finger rests in an edge zone, selecting the rows that scroll under it. */
    private suspend fun autoScroll(edgeZone: Float, maxVelocity: Float) {
        listState.scroll {
            var previousFrameNanos = withFrameNanos { it }
            while (autoScrollVelocity != 0f) {
                val frameNanos = withFrameNanos { it }
                val seconds = (frameNanos - previousFrameNanos) / 1_000_000_000f
                previousFrameNanos = frameNanos
                // Back may have cleared the selection since the last frame; the list stops here.
                val tracker = tracker ?: break
                if (endDragIfSelectionChangedElsewhere(tracker)) break
                if (dispatchScroll(autoScrollVelocity * seconds) != 0f) rangeGate?.open()
                // A collapsing top bar moves the list on screen, and with it the finger's place
                // in the list and in its edge zones.
                updateGeometry()
                selectUnderPointer()
                autoScrollVelocity = currentAutoScrollVelocity(edgeZone, maxVelocity)
            }
        }
    }

    /**
     * Scrolls by [delta] pixels, positive towards the end of the list. Nested-scroll parents are
     * offered the scroll before the list, as for a finger dragging it, so a collapsing top bar
     * collapses (or, at the top of the list, expands) first. Returns the distance the list and its
     * parents consumed.
     *
     * What the list cannot use at either end is dropped rather than passed on as post-scroll:
     * auto-scroll pressing against the top of the list is not a finger pulling it down, and a
     * pull-to-refresh parent would take it for one and refresh when the finger lifts.
     */
    private fun ScrollScope.dispatchScroll(delta: Float): Float {
        hasDispatchedNestedScroll = true
        // Nested-scroll deltas follow the finger, which moves up to scroll a list towards its end.
        val available = Offset(0f, -delta)
        val consumedByParents = nestedScrollDispatcher.dispatchPreScroll(available, NestedScrollSource.UserInput)
        val consumedByList = scrollBy(-(available - consumedByParents).y)
        return -consumedByParents.y + consumedByList
    }

    /**
     * Ends an auto-scroll for nested-scroll parents the way a finger that lifts without flinging
     * ends a drag, so a top bar left half collapsed settles. Only the post-fling is sent: the
     * pre-fling is where pull-to-refresh decides whether a pull was let go past its threshold,
     * and auto-scroll never pulls.
     */
    private suspend fun settleNestedScroll() {
        nestedScrollDispatcher.dispatchPostFling(consumed = Velocity.Zero, available = Velocity.Zero)
    }
}
