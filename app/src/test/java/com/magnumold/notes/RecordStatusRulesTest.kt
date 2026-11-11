package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordStatusRulesTest {
    @Test
    fun overdueDeadlineMarksWaitingTaskMissed() {
        assertEquals(RecordStatus.MISSED, RecordStatusRules.applyDeadline(
            RecordStatus.WAITING, RecordDateMode.DEADLINE, 100L, 86_400_100L
        ))
    }

    @Test
    fun ordinaryDateDoesNotChangeTaskStatus() {
        assertEquals(RecordStatus.WAITING, RecordStatusRules.applyDeadline(
            RecordStatus.WAITING, RecordDateMode.DATE, 100L, 101L
        ))
    }

    @Test
    fun completedAndCancelledTasksDoNotBecomeMissed() {
        for (status in listOf(RecordStatus.COMPLETED, RecordStatus.CANCELLED)) {
            assertEquals(status, RecordStatusRules.applyDeadline(
                status, RecordDateMode.DEADLINE, 100L, 101L
            ))
        }
    }

    @Test
    fun movingDeadlineDoesNotAutomaticallyResumeMissedTask() {
        assertEquals(RecordStatus.MISSED, RecordStatusRules.applyDeadline(
            RecordStatus.MISSED, RecordDateMode.DEADLINE, 10_000L, 1L
        ))
    }

    @Test
    fun deadlineBecomesMissedAtEndOfSelectedDay() {
        assertEquals(RecordStatus.MISSED, RecordStatusRules.applyDeadline(
            RecordStatus.WAITING, RecordDateMode.DEADLINE, 500L, 86_400_500L
        ))
    }

    @Test
    fun futureDeadlineKeepsTaskWaiting() {
        assertEquals(RecordStatus.WAITING, RecordStatusRules.applyDeadline(
            RecordStatus.WAITING, RecordDateMode.DEADLINE, 86_400_500L, 86_400_499L
        ))
    }
}
