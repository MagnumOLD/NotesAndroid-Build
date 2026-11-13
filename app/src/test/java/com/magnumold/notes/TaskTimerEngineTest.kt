package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTimerEngineTest {
    @Test fun remainingTimeNeverBecomesNegative() {
        assertEquals(5_000L, TaskTimerMath.remaining(15_000L, 10_000L))
        assertEquals(0L, TaskTimerMath.remaining(9_000L, 10_000L))
    }

    @Test fun elapsedTimeNeverBecomesNegative() {
        assertEquals(12_000L, TaskTimerMath.elapsed(10_000L, 22_000L))
        assertEquals(0L, TaskTimerMath.elapsed(22_000L, 10_000L))
    }

    @Test fun sessionTotalsAreScopedToTask() {
        val sessions = listOf(
            TaskTimeSession("a", 1L, 0L, 10L, 10L),
            TaskTimeSession("b", 1L, 10L, 30L, 20L),
            TaskTimeSession("c", 2L, 0L, 50L, 50L)
        )
        assertEquals(30L, TaskTimerMath.totalForTask(sessions, 1L))
        assertEquals(50L, TaskTimerMath.totalForTask(sessions, 2L))
    }

    @Test fun activeTimerKnowsBreakAndExpiry() {
        val timer = ActiveTaskTimer(1L, 1_000L, 10_000L, breakUntilMillis = 20_000L)
        assertTrue(timer.isOnBreak(15_000L))
        assertFalse(timer.isOnBreak(21_000L))
        assertTrue(timer.isExpired(20_000L))
    }

    @Test fun preferencesAreClampedToSupportedRanges() {
        val value = TimerPreferences(0, TimerEndMode.BREAK, 999, true).normalized()
        assertEquals(1, value.defaultLimitMinutes)
        assertEquals(90, value.breakMinutes)
    }
}
