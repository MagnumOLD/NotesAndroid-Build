package com.magnumold.notes

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

internal const val NOTE_REORDER_ANIMATION_DURATION_MILLIS = 220
private const val REORDER_VIEWPORT_ANCHOR_ITEM_COUNT = 1
private const val REORDER_LAYOUT_TIMEOUT_MILLIS = 2_000L

/**
 * Coordinates one drag session.
 *
 * A neighbouring exchange is unlocked only after both stable keys reach their
 * requested layout indices and one full placement-animation duration has
 * elapsed from a rendered frame after that layout was applied.
 */
@Composable
internal fun AdjacentReorderEffect(
    isDragging: Boolean,
    dragRows: List<DragRow>,
    draggingIds: Set<Long>,
    reorderSession: ReorderSession?,
    pointerY: Float,
    grabOffsetPx: Float,
    releaseRequested: Boolean,
    listState: LazyListState,
    density: Density,
    onSessionChange: (ReorderSession) -> Unit,
    onFinishDrag: () -> Unit
) {
    val latestIsDragging = rememberUpdatedState(isDragging)
    val latestDragRows = rememberUpdatedState(dragRows)
    val latestReorderSession = rememberUpdatedState(reorderSession)
    val latestDraggingIds = rememberUpdatedState(draggingIds)
    val latestPointerY = rememberUpdatedState(pointerY)
    val latestReleaseRequested = rememberUpdatedState(releaseRequested)
    val latestFinishDrag = rememberUpdatedState(onFinishDrag)
    val latestSessionChange = rememberUpdatedState(onSessionChange)

    LaunchedEffect(isDragging) {
        if (!isDragging) return@LaunchedEffect

        while (latestIsDragging.value) {
            if (latestReleaseRequested.value) {
                latestFinishDrag.value.invoke()
                break
            }

            val rows = latestDragRows.value
            val ids = latestDraggingIds.value
            val movingRowIndex = rows.indexOfFirst { row -> row.notes.any { it.id in ids } }

            if (movingRowIndex >= 0) {
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                val movingKey = rows[movingRowIndex].key
                val movingInfo = visibleItems.firstOrNull { it.key == movingKey }
                val previousInfo = rows.getOrNull(movingRowIndex - 1)?.let { adjacent ->
                    visibleItems.firstOrNull { it.key == adjacent.key }
                }
                val nextInfo = rows.getOrNull(movingRowIndex + 1)?.let { adjacent ->
                    visibleItems.firstOrNull { it.key == adjacent.key }
                }

                val direction = movingInfo?.let {
                    AdjacentReorderController.crossedNeighbour(
                        pointerY = latestPointerY.value,
                        grabOffset = grabOffsetPx,
                        movingHeight = it.size.toFloat(),
                        previous = previousInfo?.let { item ->
                            ReorderItemBounds(item.offset.toFloat(), (item.offset + item.size).toFloat())
                        },
                        next = nextInfo?.let { item ->
                            ReorderItemBounds(item.offset.toFloat(), (item.offset + item.size).toFloat())
                        }
                    )
                }

                // Pinned notes form a fixed leading partition. Mixed-pinned groups
                // cannot be exchanged because they do not belong to one partition.
                val movingPinStates = rows.getOrNull(movingRowIndex)
                    ?.notes?.map { it.pinned }?.distinct().orEmpty()
                val adjacentIndex = when (direction) {
                    ReorderDirection.TowardStart -> movingRowIndex - 1
                    ReorderDirection.TowardEnd -> movingRowIndex + 1
                    null -> -1
                }
                val adjacentPinStates = rows.getOrNull(adjacentIndex)
                    ?.notes?.map { it.pinned }?.distinct().orEmpty()
                val allowedDirection = direction?.takeIf {
                    movingPinStates.size == 1 && adjacentPinStates.size == 1 &&
                        movingPinStates.single() == adjacentPinStates.single()
                }

                if (allowedDirection != null) {
                    if (latestReleaseRequested.value) {
                        latestFinishDrag.value.invoke()
                        break
                    }

                    val currentSession = latestReorderSession.value ?: break
                    val swappingSession = AdjacentReorderEngine.requestSwap(currentSession, allowedDirection)
                    if (swappingSession == null) {
                        delay(16)
                        continue
                    }

                    val expectedMovingIndex = swappingSession.expectedIndex ?: break
                    val neighborKey = swappingSession.expectedNeighborKey ?: break
                    val expectedNeighborIndex = swappingSession.expectedNeighborIndex ?: break

                    // Preserve the first visible keyed row only when a swap touches
                    // the current scrolled anchor. At absolute top, the permanent
                    // 1dp sentinel already owns the viewport anchor.
                    val firstVisibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                    val firstVisibleIndex = listState.firstVisibleItemIndex
                    val firstVisibleOffset = listState.firstVisibleItemScrollOffset
                    val mustPreserveScrolledAnchor =
                        firstVisibleItem?.key == movingKey || firstVisibleItem?.key == neighborKey

                    latestSessionChange.value.invoke(swappingSession)

                    if (mustPreserveScrolledAnchor) {
                        listState.requestScrollToItem(firstVisibleIndex, firstVisibleOffset)
                    }

                    // LazyListItemInfo.offset is the target layout offset; it does
                    // not expose animateItemPlacement's in-flight visual offset.
                    // First wait for both stable keys to reach the committed indices,
                    // then wait by frame time for the same duration as the placement
                    // tween. This avoids mistaking rounded, temporarily unchanged
                    // integer offsets for a finished animation.
                    val placementFinished = withTimeoutOrNull(REORDER_LAYOUT_TIMEOUT_MILLIS) {
                        var layoutApplied = false
                        while (latestIsDragging.value && !latestReleaseRequested.value && !layoutApplied) {
                            withFrameNanos { }
                            val measured = listState.layoutInfo.visibleItemsInfo
                            val movingActual = measured.firstOrNull { it.key == movingKey }
                            val neighborActual = measured.firstOrNull { it.key == neighborKey }
                            layoutApplied =
                                movingActual?.index == expectedMovingIndex + REORDER_VIEWPORT_ANCHOR_ITEM_COUNT &&
                                neighborActual?.index == expectedNeighborIndex + REORDER_VIEWPORT_ANCHOR_ITEM_COUNT
                        }

                        if (!layoutApplied) {
                            false
                        } else {
                            var firstFrameNanos: Long? = null
                            var elapsedNanos = 0L
                            val durationNanos = NOTE_REORDER_ANIMATION_DURATION_MILLIS * 1_000_000L
                            while (
                                elapsedNanos < durationNanos &&
                                latestIsDragging.value &&
                                !latestReleaseRequested.value
                            ) {
                                val frameNanos = withFrameNanos { it }
                                if (firstFrameNanos == null) firstFrameNanos = frameNanos
                                elapsedNanos = frameNanos - firstFrameNanos!!
                            }
                            elapsedNanos >= durationNanos
                        }
                    } ?: false

                    if (latestReleaseRequested.value) {
                        latestFinishDrag.value.invoke()
                        break
                    }
                    if (!latestIsDragging.value) break
                    if (!placementFinished) {
                        // A missing layout acknowledgement must not leave the
                        // reorder state machine locked indefinitely.
                        latestFinishDrag.value.invoke()
                        break
                    }

                    val measured = listState.layoutInfo.visibleItemsInfo
                    val movingActual = measured.firstOrNull { it.key == movingKey }
                    val neighborActual = measured.firstOrNull { it.key == neighborKey }
                    val completedSession = if (movingActual != null && neighborActual != null) {
                        AdjacentReorderEngine.completeSwap(
                            swappingSession,
                            actualMovingIndex = movingActual.index - REORDER_VIEWPORT_ANCHOR_ITEM_COUNT,
                            actualNeighborIndex = neighborActual.index - REORDER_VIEWPORT_ANCHOR_ITEM_COUNT
                        )
                    } else null

                    if (completedSession == null) {
                        latestFinishDrag.value.invoke()
                        break
                    }

                    latestSessionChange.value.invoke(completedSession)
                    continue
                }
            }

            // Edge scrolling is independent of a neighbour exchange.
            val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
            val edgeSize = with(density) { 64.dp.toPx() }
            val maxStep = with(density) { 12.dp.toPx() }
            val y = latestPointerY.value
            val scrollDelta = when {
                y < edgeSize -> -maxStep * ((edgeSize - y) / edgeSize).coerceIn(0f, 1f)
                y > viewportHeight - edgeSize ->
                    maxStep * ((y - (viewportHeight - edgeSize)) / edgeSize).coerceIn(0f, 1f)
                else -> 0f
            }
            if (scrollDelta != 0f) listState.scrollBy(scrollDelta)
            delay(16)
        }
    }
}
