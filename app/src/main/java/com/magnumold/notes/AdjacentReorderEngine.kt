package com.magnumold.notes

/**
 * Pure ordering state machine for note dragging.
 *
 * It deliberately knows nothing about Compose, pointer coordinates, or animation.
 * The UI supplies a stable ordered key list and acknowledges a committed swap
 * only after the corresponding layout transition has completed.
 */
internal enum class ReorderDirection {
    TowardStart,
    TowardEnd
}

internal enum class ReorderPhase {
    Tracking,
    Animating
}

internal data class ReorderSession(
    val keys: List<String>,
    val movingKey: String,
    val phase: ReorderPhase = ReorderPhase.Tracking,
    val expectedIndex: Int? = null,
    val expectedNeighborKey: String? = null,
    val expectedNeighborIndex: Int? = null
) {
    val movingIndex: Int
        get() = keys.indexOf(movingKey)
}

internal object AdjacentReorderEngine {

    fun begin(keys: List<String>, movingKey: String): ReorderSession {
        require(keys.isNotEmpty()) { "A reorder session needs at least one item" }
        require(keys.size == keys.toSet().size) { "Reorder keys must be unique" }
        require(movingKey in keys) { "The moving item must exist in the ordered list" }
        return ReorderSession(keys = keys.toList(), movingKey = movingKey)
    }

    /**
     * Requests exactly one neighbouring exchange. While the previous exchange
     * is animating, another request is rejected instead of being queued implicitly.
     */
    fun requestSwap(
        session: ReorderSession,
        direction: ReorderDirection
    ): ReorderSession? {
        if (session.phase != ReorderPhase.Tracking) return null

        val from = session.movingIndex
        if (from < 0) return null

        val to = from + when (direction) {
            ReorderDirection.TowardStart -> -1
            ReorderDirection.TowardEnd -> 1
        }
        if (to !in session.keys.indices) return null

        val reordered = session.keys.toMutableList()
        val neighborKey = reordered[to]
        val moving = reordered.removeAt(from)
        reordered.add(to, moving)

        return session.copy(
            keys = reordered,
            phase = ReorderPhase.Animating,
            expectedIndex = to,
            expectedNeighborKey = neighborKey,
            expectedNeighborIndex = from
        )
    }

    /**
     * Unlocks the next exchange only when the UI confirms the moving key reached
     * the exact index requested by the state machine.
     */
    fun completeSwap(
        session: ReorderSession,
        actualMovingIndex: Int,
        actualNeighborIndex: Int
    ): ReorderSession? {
        if (session.phase != ReorderPhase.Animating) return null
        val neighborKey = session.expectedNeighborKey ?: return null
        val expectedNeighborIndex = session.expectedNeighborIndex ?: return null
        if (session.expectedIndex != actualMovingIndex) return null
        if (session.movingIndex != actualMovingIndex) return null
        if (expectedNeighborIndex != actualNeighborIndex) return null
        if (session.keys.indexOf(neighborKey) != actualNeighborIndex) return null

        return session.copy(
            phase = ReorderPhase.Tracking,
            expectedIndex = null,
            expectedNeighborKey = null,
            expectedNeighborIndex = null
        )
    }

}
