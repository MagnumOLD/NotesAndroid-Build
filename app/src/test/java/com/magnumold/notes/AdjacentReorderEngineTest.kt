package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdjacentReorderEngineTest {

    @Test
    fun swapsOnlyOneNeighbourPerRequest() {
        val started = AdjacentReorderEngine.begin(listOf("a", "b", "c", "d"), "b")

        val first = AdjacentReorderEngine.requestSwap(started, ReorderDirection.TowardEnd)

        assertEquals(listOf("a", "c", "b", "d"), first?.keys)
        assertEquals(ReorderPhase.Animating, first?.phase)
        assertNull(AdjacentReorderEngine.requestSwap(first!!, ReorderDirection.TowardEnd))
    }

    @Test
    fun waitsForTheExpectedIndexBeforeUnlocking() {
        val started = AdjacentReorderEngine.begin(listOf("a", "b", "c"), "b")
        val swapping = AdjacentReorderEngine.requestSwap(started, ReorderDirection.TowardEnd)!!

        assertNull(AdjacentReorderEngine.completeSwap(swapping, actualMovingIndex = 1, actualNeighborIndex = 2))

        val ready = AdjacentReorderEngine.completeSwap(swapping, actualMovingIndex = 2, actualNeighborIndex = 1)
        assertEquals(ReorderPhase.Tracking, ready?.phase)
        assertNull(ready?.expectedNeighborKey)
        assertNull(ready?.expectedNeighborIndex)
        assertNull(ready?.expectedIndex)
    }


    @Test
    fun doesNotCompleteUntilTheDisplacedNeighbourAlsoReachesItsTarget() {
        val started = AdjacentReorderEngine.begin(listOf("a", "b", "c"), "b")
        val swapping = AdjacentReorderEngine.requestSwap(started, ReorderDirection.TowardStart)!!

        assertNull(AdjacentReorderEngine.completeSwap(swapping, actualMovingIndex = 0, actualNeighborIndex = 2))

        val completed = AdjacentReorderEngine.completeSwap(
            swapping,
            actualMovingIndex = 0,
            actualNeighborIndex = 1
        )
        assertEquals(ReorderPhase.Tracking, completed?.phase)
        assertEquals(listOf("b", "a", "c"), completed?.keys)
    }

    @Test
    fun supportsDirectionReversalAfterCompletedSwap() {
        val started = AdjacentReorderEngine.begin(listOf("a", "b", "c", "d"), "b")
        val down = AdjacentReorderEngine.requestSwap(started, ReorderDirection.TowardEnd)!!
        val readyAfterDown = AdjacentReorderEngine.completeSwap(down, 2, 1)!!

        val up = AdjacentReorderEngine.requestSwap(readyAfterDown, ReorderDirection.TowardStart)!!

        assertEquals(listOf("a", "b", "c", "d"), up.keys)
        assertEquals(1, up.expectedIndex)
    }

    @Test
    fun doesNotMovePastEitherBoundary() {
        val atStart = AdjacentReorderEngine.begin(listOf("a", "b", "c"), "a")
        val atEnd = AdjacentReorderEngine.begin(listOf("a", "b", "c"), "c")

        assertNull(AdjacentReorderEngine.requestSwap(atStart, ReorderDirection.TowardStart))
        assertNull(AdjacentReorderEngine.requestSwap(atEnd, ReorderDirection.TowardEnd))
    }

    @Test
    fun supportsGroupAsOneStableKey() {
        val started = AdjacentReorderEngine.begin(listOf("a", "group-1-2-3", "d"), "group-1-2-3")
        val moved = AdjacentReorderEngine.requestSwap(started, ReorderDirection.TowardEnd)

        assertEquals(listOf("a", "d", "group-1-2-3"), moved?.keys)
    }
}
