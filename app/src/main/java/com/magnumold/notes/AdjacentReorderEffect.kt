package com.magnumold.notes

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull

internal const val NOTE_REORDER_ANIMATION_DURATION_MILLIS = 220
private const val REORDER_LAYOUT_TIMEOUT_MILLIS = 2_000L
private const val REORDER_EDGE_SIZE_DP = 64f
private const val REORDER_EDGE_SPEED_DP_PER_SECOND = 720f

/**
 * Shared coordinator for note cards and nested tasks.
 *
 * Both lists supply stable keys and measured LazyList geometry. The coordinator
 * owns neighbour detection, one-swap-at-a-time sequencing, layout acknowledgement,
 * animation settling, and frame-rate-independent edge scrolling. Domain-specific
 * ordering rules remain in canSwap and the caller's finish callback.
 */
@Composable
internal fun AdjacentReorderEffect(
    isDragging: Boolean,
    orderedKeys: List<String>,
    reorderSession: ReorderSession?,
    pointerY: Float,
    grabOffsetPx: Float,
    releaseRequested: Boolean,
    listState: LazyListState,
    density: Density,
    canSwap: (movingKey: String, neighbourKey: String) -> Boolean = { _, _ -> true },
    onSessionChange: (ReorderSession) -> Unit,
    onFinishDrag: () -> Unit
) {
    val latestIsDragging = rememberUpdatedState(isDragging)
    val latestOrderedKeys = rememberUpdatedState(orderedKeys)
    val latestReorderSession = rememberUpdatedState(reorderSession)
    val latestPointerY = rememberUpdatedState(pointerY)
    val latestGrabOffset = rememberUpdatedState(grabOffsetPx)
    val latestReleaseRequested = rememberUpdatedState(releaseRequested)
    val latestCanSwap = rememberUpdatedState(canSwap)
    val latestFinishDrag = rememberUpdatedState(onFinishDrag)
    val latestSessionChange = rememberUpdatedState(onSessionChange)

    LaunchedEffect(isDragging) {
        if (!isDragging) return@LaunchedEffect

        var previousFrameNanos = 0L
        while (latestIsDragging.value) {
            if (latestReleaseRequested.value) {
                latestFinishDrag.value.invoke()
                break
            }

            val session = latestReorderSession.value
            val keys = latestOrderedKeys.value
            if (session == null || keys.isEmpty() || session.movingKey !in keys) {
                latestFinishDrag.value.invoke()
                break
            }

            val movingIndex = keys.indexOf(session.movingKey)
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val movingInfo = visibleItems.firstOrNull { it.key == session.movingKey }
            val previousKey = keys.getOrNull(movingIndex - 1)
            val nextKey = keys.getOrNull(movingIndex + 1)
            val previousInfo = previousKey?.let { key -> visibleItems.firstOrNull { it.key == key } }
            val nextInfo = nextKey?.let { key -> visibleItems.firstOrNull { it.key == key } }

            val direction = movingInfo?.let {
                AdjacentReorderController.crossedNeighbour(
                    pointerY = latestPointerY.value,
                    grabOffset = latestGrabOffset.value,
                    movingHeight = it.size.toFloat(),
                    previous = previousInfo?.let { item ->
                        ReorderItemBounds(item.offset.toFloat(), (item.offset + item.size).toFloat())
                    },
                    next = nextInfo?.let { item ->
                        ReorderItemBounds(item.offset.toFloat(), (item.offset + item.size).toFloat())
                    }
                )
            }

            val neighbourKey = when (direction) {
                ReorderDirection.TowardStart -> previousKey
                ReorderDirection.TowardEnd -> nextKey
                null -> null
            }

            if (direction != null && neighbourKey != null &&
                latestCanSwap.value.invoke(session.movingKey, neighbourKey)
            ) {
                if (latestReleaseRequested.value) {
                    latestFinishDrag.value.invoke()
                    break
                }

                val swappingSession = AdjacentReorderEngine.requestSwap(session, direction)
                if (swappingSession == null) {
                    // The previous adjacent exchange is still animating.
                    // Wait for a rendered frame rather than consuming input events.
                    withFrameNanos { }
                    continue
                }

                val expectedMovingIndex = swappingSession.expectedIndex ?: break
                val expectedNeighbourKey = swappingSession.expectedNeighborKey ?: break
                val expectedNeighbourIndex = swappingSession.expectedNeighborIndex ?: break

                // LazyList indices include all non-reorder items (editor content,
                // viewport sentinels, headers). Derive the stable offset from any
                // currently visible reorder row instead of assuming a fixed index.
                val indexOffset = visibleItems.firstNotNullOfOrNull { item ->
                    val key = item.key as? String ?: return@firstNotNullOfOrNull null
                    val relativeIndex = keys.indexOf(key)
                    if (relativeIndex >= 0) item.index - relativeIndex else null
                } ?: 0

                val firstVisibleItem = visibleItems.firstOrNull()
                val firstVisibleIndex = listState.firstVisibleItemIndex
                val firstVisibleOffset = listState.firstVisibleItemScrollOffset
                val preserveViewportAnchor =
                    firstVisibleItem?.key == session.movingKey ||
                        firstVisibleItem?.key == expectedNeighbourKey

                latestSessionChange.value.invoke(swappingSession)
                if (preserveViewportAnchor) {
                    listState.requestScrollToItem(firstVisibleIndex, firstVisibleOffset)
                }

                // Wait until both stable keys reach their requested indices, then
                // let the placement animation finish for its full configured time.
                val placementFinished = withTimeoutOrNull(REORDER_LAYOUT_TIMEOUT_MILLIS) {
                    var layoutApplied = false
                    while (latestIsDragging.value && !latestReleaseRequested.value && !layoutApplied) {
                        withFrameNanos { }
                        val measured = listState.layoutInfo.visibleItemsInfo
                        val movingActual = measured.firstOrNull { it.key == session.movingKey }
                        val neighbourActual = measured.firstOrNull { it.key == expectedNeighbourKey }
                        layoutApplied =
                            movingActual?.index == expectedMovingIndex + indexOffset &&
                                neighbourActual?.index == expectedNeighbourIndex + indexOffset
                    }

                    // A swap is complete as soon as LazyColumn has applied the new
                    // stable-key indices. Do not wait for the visual placement animation:
                    // it can be retargeted smoothly by Compose while the finger keeps moving.
                    // Waiting for the full animation here throttles input to one swap per 220ms.
                    layoutApplied
                } ?: false

                if (latestReleaseRequested.value) {
                    latestFinishDrag.value.invoke()
                    break
                }
                if (!latestIsDragging.value) break
                if (!placementFinished) {
                    latestFinishDrag.value.invoke()
                    break
                }

                val measured = listState.layoutInfo.visibleItemsInfo
                val movingActual = measured.firstOrNull { it.key == session.movingKey }
                val neighbourActual = measured.firstOrNull { it.key == expectedNeighbourKey }
                val completedSession = if (movingActual != null && neighbourActual != null) {
                    AdjacentReorderEngine.completeSwap(
                        swappingSession,
                        actualMovingIndex = movingActual.index - indexOffset,
                        actualNeighborIndex = neighbourActual.index - indexOffset
                    )
                } else null

                if (completedSession == null) {
                    latestFinishDrag.value.invoke()
                    break
                }

                latestSessionChange.value.invoke(completedSession)
                previousFrameNanos = 0L
                continue
            }

            // Edge scrolling is driven by frame time, not pointer-event frequency.
            val frameNanos = withFrameNanos { it }
            val deltaSeconds = if (previousFrameNanos == 0L) {
                0f
            } else {
                ((frameNanos - previousFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            }
            previousFrameNanos = frameNanos

            val layout = listState.layoutInfo
            val viewportStart = layout.viewportStartOffset.toFloat()
            val viewportEnd = layout.viewportEndOffset.toFloat()
            val edgeSize = with(density) { REORDER_EDGE_SIZE_DP.dp.toPx() }
            val maxSpeed = with(density) { REORDER_EDGE_SPEED_DP_PER_SECOND.dp.toPx() }
            val y = latestPointerY.value
            val edgeProgress = when {
                y < viewportStart + edgeSize ->
                    -((viewportStart + edgeSize - y) / edgeSize).coerceIn(0f, 1f)
                y > viewportEnd - edgeSize ->
                    ((y - (viewportEnd - edgeSize)) / edgeSize).coerceIn(0f, 1f)
                else -> 0f
            }
            if (edgeProgress != 0f && deltaSeconds > 0f) {
                listState.scrollBy(maxSpeed * edgeProgress * deltaSeconds)
            }
        }
    }
}
