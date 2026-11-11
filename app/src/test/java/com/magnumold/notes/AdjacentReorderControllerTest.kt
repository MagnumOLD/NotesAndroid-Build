package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdjacentReorderControllerTest {

    @Test
    fun swapsDownWhenGrabbedCardCentreCrossesNextCentre() {
        val direction = AdjacentReorderController.crossedNeighbour(
            pointerY = 220f,
            grabOffset = 20f,
            movingHeight = 100f,
            previous = null,
            next = ReorderItemBounds(190f, 290f)
        )
        assertEquals(ReorderDirection.TowardEnd, direction)
    }

    @Test
    fun swapsUpWhenGrabbedCardCentreCrossesPreviousCentre() {
        val direction = AdjacentReorderController.crossedNeighbour(
            pointerY = 60f,
            grabOffset = 80f,
            movingHeight = 100f,
            previous = ReorderItemBounds(0f, 100f),
            next = null
        )
        assertEquals(ReorderDirection.TowardStart, direction)
    }

    @Test
    fun usesActualGroupHeightAndGrabOffset() {
        val direction = AdjacentReorderController.crossedNeighbour(
            pointerY = 290f,
            grabOffset = 30f,
            movingHeight = 180f,
            previous = null,
            next = ReorderItemBounds(300f, 380f)
        )
        assertEquals(ReorderDirection.TowardEnd, direction)
    }

    @Test
    fun doesNotSwapAtEitherBoundaryOrBeforeCrossing() {
        assertNull(AdjacentReorderController.crossedNeighbour(
            pointerY = 40f, grabOffset = 20f, movingHeight = 80f,
            previous = null, next = ReorderItemBounds(100f, 180f)
        ))
        assertNull(AdjacentReorderController.crossedNeighbour(
            pointerY = 120f, grabOffset = 20f, movingHeight = 80f,
            previous = ReorderItemBounds(0f, 80f), next = null
        ))
    }

    @Test
    fun rejectsInvalidGeometry() {
        assertNull(AdjacentReorderController.crossedNeighbour(
            pointerY = Float.NaN, grabOffset = 0f, movingHeight = 80f,
            previous = null, next = ReorderItemBounds(100f, 180f)
        ))
    }
}
