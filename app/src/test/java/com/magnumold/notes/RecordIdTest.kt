package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordIdTest {
    @Test
    fun generatedRecordIdsArePositiveAndUniqueAcrossBatch() {
        val ids = List(1_000) { newRecordId() }
        assertTrue(ids.all { it > 0L })
        assertEquals(ids.size, ids.toSet().size)
    }
}
