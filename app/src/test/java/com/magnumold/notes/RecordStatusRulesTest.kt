package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordStatusRulesTest {
    @Test
    fun overdueDeadlineMarksWaitingTaskMissed() {
        assertEquals(
            RecordStatus.MISSED,
            RecordStatusRules.applyDeadline(
                RecordStatus.WAITING,
                RecordDateMode.DEADLINE,
                dateAt = 100L,
                now = 86_400_100L
            )
        )
    }

    @Test
    fun ordinaryDateDoesNotChangeTaskStatus() {
        assertEquals(
            RecordStatus.WAITING,
            RecordStatusRules.applyDeadline(
                RecordStatus.WAITING,
                RecordDateMode.DATE,
                dateAt = 100L,
                now = 101L
            )
        )
    }

    @Test
    fun completedAndCancelledTasksDoNotBecomeMissed() {
        for (status in listOf(RecordStatus.COMPLETED, RecordStatus.CANCELLED)) {
            assertEquals(
                status,
                RecordStatusRules.applyDeadline(status, RecordDateMode.DEADLINE, 100L, 101L)
            )
        }
    }

    @Test
    fun movingDeadlineDoesNotAutomaticallyResumeMissedTask() {
        assertEquals(
            RecordStatus.MISSED,
            RecordStatusRules.applyDeadline(
                RecordStatus.MISSED,
                RecordDateMode.DEADLINE,
                dateAt = 10_000L,
                now = 1L
            )
        )
    }

    @Test
    fun universalBecomesCompletedWhenAllChildrenAreResolved() {
        val parent = listOf(
            RecordNode(1, RecordType.TASK, RecordStatus.COMPLETED),
            RecordNode(2, RecordType.TASK, RecordStatus.CANCELLED)
        )
        assertEquals(RecordStatus.COMPLETED, RecordStatusRules.deriveUniversalStatus(parent))
    }

    @Test
    fun universalPropagatesMissedAndInProgressStatuses() {
        assertEquals(
            RecordStatus.MISSED,
            RecordStatusRules.deriveUniversalStatus(
                listOf(RecordNode(1, RecordType.TASK, RecordStatus.MISSED))
            )
        )
        assertEquals(
            RecordStatus.IN_PROGRESS,
            RecordStatusRules.deriveUniversalStatus(
                listOf(RecordNode(1, RecordType.TASK, RecordStatus.IN_PROGRESS))
            )
        )
    }

    @Test
    fun universalCanContainOnlyTasksAndUniversalRecords() {
        assertTrue(RecordStatusRules.canContain(RecordType.UNIVERSAL, RecordType.TASK))
        assertTrue(RecordStatusRules.canContain(RecordType.UNIVERSAL, RecordType.UNIVERSAL))
        assertFalse(RecordStatusRules.canContain(RecordType.TASK, RecordType.TASK))
        assertFalse(RecordStatusRules.canContain(RecordType.NOTE, RecordType.UNIVERSAL))
    }
    @Test
    fun deadlineBecomesMissedAtTheEndOfSelectedDay() {
        assertEquals(
            RecordStatus.MISSED,
            RecordStatusRules.applyDeadline(
                RecordStatus.WAITING,
                RecordDateMode.DEADLINE,
                dateAt = 500L,
                now = 86_400_500L
            )
        )
    }

    @Test
    fun futureDeadlineKeepsTaskWaiting() {
        assertEquals(
            RecordStatus.WAITING,
            RecordStatusRules.applyDeadline(
                RecordStatus.WAITING,
                RecordDateMode.DEADLINE,
                dateAt = 86_400_500L,
                now = 86_400_499L
            )
        )
    }

    @Test
    fun nestedUniversalStatusIsDerivedRecursively() {
        val nested = RecordNode(
            id = 2,
            type = RecordType.UNIVERSAL,
            children = listOf(
                RecordNode(3, RecordType.TASK, RecordStatus.COMPLETED),
                RecordNode(4, RecordType.TASK, RecordStatus.CANCELLED)
            )
        )
        assertEquals(
            RecordStatus.COMPLETED,
            RecordStatusRules.deriveUniversalStatus(listOf(nested))
        )
    }

    @Test
    fun emptyUniversalRemainsWaiting() {
        assertEquals(
            RecordStatus.WAITING,
            RecordStatusRules.deriveUniversalStatus(emptyList())
        )
    }

}
