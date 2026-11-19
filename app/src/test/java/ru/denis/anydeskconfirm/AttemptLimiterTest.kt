package ru.denis.anydeskconfirm

import org.junit.Assert.*
import org.junit.Test
import ru.denis.anydeskconfirm.accessibility.AttemptLimiter

class AttemptLimiterTest {
    @Test fun successBlocksEventStormAndLateEvents() {
        val gate = AttemptLimiter()
        assertTrue(gate.observe(1, 0))
        assertTrue(gate.canAttempt(1, 0, true))
        gate.record(1, 0, true)
        for (t in 1L..10_000L) {
            assertFalse(gate.observe(1, t))
            assertFalse(gate.canAttempt(1, t, true))
        }
        assertFalse(gate.canAttempt(1, 1_000_000, true))
    }
    @Test fun failureHasCooldownAndThreeAttemptLimit() {
        val gate = AttemptLimiter(); gate.observe(1, 0)
        repeat(3) { attempt ->
            val now = attempt * 700L
            assertTrue(gate.canAttempt(1, now, true)); gate.record(1, now, false)
            assertFalse(gate.canAttempt(1, now + 1, true))
        }
        assertFalse(gate.canAttempt(1, 2100, true)); assertFalse(gate.retryAllowed(1, 2100))
    }
    @Test fun anotherDialogIsAllowedAfterGlobalCooldown() {
        val gate = AttemptLimiter(); gate.observe(1, 0); gate.record(1, 0, true)
        gate.observe(2, 100)
        assertFalse(gate.canAttempt(2, 100, true))
        assertTrue(gate.retryAllowed(2, 100))
        assertTrue(gate.canAttempt(2, 700, true))
        assertFalse(gate.canAttempt(1, 700, true))
    }
    @Test fun disabledAndExpiredNeverAct() {
        val gate = AttemptLimiter(); gate.observe(1, 0)
        assertFalse(gate.canAttempt(1, 0, false))
        assertFalse(gate.canAttempt(1, 8001, true))
        assertFalse(gate.retryAllowed(1, 8001))
        assertFalse(gate.canAttempt(2, 0, true))
    }
    @Test fun checkboxIsAttemptedOnlyOncePerWindow() {
        val gate = AttemptLimiter(); gate.observe(1, 0)
        assertTrue(gate.needsRemember(1)); gate.markRemember(1)
        assertFalse(gate.needsRemember(1)); gate.observe(1, 1)
        assertFalse(gate.needsRemember(1))
    }
}
