package com.example.handsfree

import com.example.handsfree.agent.LoopGuard
import org.junit.Assert.*
import org.junit.Test

class LoopGuardTest {
    @Test fun changingDuringReasoningHasABoundedRetry() {
        val guard = LoopGuard()
        assertNull(guard.checkFreshness(false))
        assertNull(guard.checkFreshness(false))
        assertNotNull(guard.checkFreshness(false))
        assertNull(guard.checkFreshness(true))
        assertNull(guard.checkFreshness(false))
    }
    @Test fun unchangedRepeatedActionsStop() {
        val guard = LoopGuard()
        assertNull(guard.check("click:e1", "obs1"))
        assertNull(guard.check("click:e1", "obs1"))
        assertNotNull(guard.check("click:e1", "obs1"))
    }
    @Test fun changingScreensStillHaveAFiniteBudget() {
        val guard = LoopGuard()
        repeat(40) { assertNull(guard.check("click", "obs$it")) }
        assertNotNull(guard.check("click", "obs41"))
    }
}
