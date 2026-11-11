package com.magnumold.notes

/**
 * Geometry supplied by the UI for one neighbouring row.
 * Coordinates use the same local space as the active pointer.
 */
internal data class ReorderItemBounds(
    val top: Float,
    val bottom: Float
) {
    val center: Float get() = (top + bottom) / 2f
}

/**
 * Converts the active pointer position into a single adjacent-swap decision.
 *
 * The grabbed point stays under the finger. The moving row's current measured
 * height is used, so a merged group and the first/last rows need no special
 * fixed-height assumptions.
 */
internal object AdjacentReorderController {

    fun crossedNeighbour(
        pointerY: Float,
        grabOffset: Float,
        movingHeight: Float,
        previous: ReorderItemBounds?,
        next: ReorderItemBounds?
    ): ReorderDirection? {
        if (!pointerY.isFinite() || !grabOffset.isFinite() ||
            !movingHeight.isFinite() || movingHeight <= 0f
        ) return null

        val draggedCenter = pointerY - grabOffset + movingHeight / 2f
        val crossedNext = next != null && draggedCenter > next.center
        val crossedPrevious = previous != null && draggedCenter < previous.center

        // A pointer cannot normally cross both neighbours in one frame. If an
        // unusually large input delta does so, prefer the closest crossed edge.
        if (crossedNext && crossedPrevious) {
            val nextDistance = draggedCenter - next!!.center
            val previousDistance = previous!!.center - draggedCenter
            return if (nextDistance <= previousDistance) {
                ReorderDirection.TowardEnd
            } else {
                ReorderDirection.TowardStart
            }
        }

        return when {
            crossedNext -> ReorderDirection.TowardEnd
            crossedPrevious -> ReorderDirection.TowardStart
            else -> null
        }
    }
}
