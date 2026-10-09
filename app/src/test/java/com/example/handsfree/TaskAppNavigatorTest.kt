package com.example.handsfree

import com.example.handsfree.agent.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class TaskAppNavigatorTest {
    private val host = "com.example.handsfree"
    private val target = "com.example.notes"
    private fun screen(pkg: String, blocked: String? = null) = ScreenObservation(pkg, pkg, emptyList(), blocked)

    @Test fun typedReplyReturnsToApprovedAppBeforePlanning() = runBlocking {
        val navigator = TaskAppNavigator(host).apply { rememberOpened(target) }
        var visible = screen(host)
        var launches = 0
        val result = navigator.observeForPlanning(setOf(target), { visible }, { pkg, before ->
            assertEquals(host, before.packageName)
            assertEquals(target, pkg)
            launches++
            ActionOutcome(true, "Launch requested")
        }, { visible = screen(target) })
        assertEquals(target, result.packageName)
        assertEquals(1, launches)
    }

    @Test fun voiceReplyAlreadyInTargetDoesNotRelaunch() = runBlocking {
        val navigator = TaskAppNavigator(host).apply { rememberOpened(target) }
        val result = navigator.observeForPlanning(setOf(target), { screen(target) },
            { _, _ -> error("Must not relaunch") }, { error("No launch wait needed") })
        assertEquals(target, result.packageName)
    }

    @Test fun newTaskDoesNotGuessAnApp() = runBlocking {
        val result = TaskAppNavigator(host).observeForPlanning(emptySet(), { screen(host) },
            { _, _ -> error("No app was selected") }, {})
        assertEquals(host, result.packageName)
    }

    @Test fun unrelatedAppIsNotReplacedWithoutUserChoice() = runBlocking {
        val navigator = TaskAppNavigator(host).apply { rememberOpened(target) }
        val result = navigator.observeForPlanning(setOf(target), { screen("com.example.other") },
            { _, _ -> error("Must not interrupt another app") }, {})
        assertEquals("com.example.other", result.packageName)
    }

    @Test fun revokedApprovalCannotBeUsedToResume() = runBlocking {
        val navigator = TaskAppNavigator(host).apply { rememberOpened(target) }
        val result = navigator.observeForPlanning(emptySet(), { screen(host) },
            { _, _ -> error("Approval required") }, {})
        assertEquals(host, result.packageName)
    }

    @Test fun protectedScreenIsNotLeftAutomatically() = runBlocking {
        val navigator = TaskAppNavigator(host).apply { rememberOpened(target) }
        val result = navigator.observeForPlanning(setOf(target), { screen(host, "Protected") },
            { _, _ -> error("Privacy stop must win") }, {})
        assertEquals("Protected", result.blockedReason)
    }

    @Test fun failedLaunchDoesNotReturnAnEmptyHostScreenToPlanner() = runBlocking {
        val navigator = TaskAppNavigator(host).apply { rememberOpened(target) }
        val failure = runCatching { navigator.observeForPlanning(setOf(target), { screen(host) },
            { _, _ -> ActionOutcome(false, "Unavailable") }, {}) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }

    @Test fun launchThatNeverReachesTargetHasBoundedWait() = runBlocking {
        val navigator = TaskAppNavigator(host).apply { rememberOpened(target) }
        var waits = 0
        val failure = runCatching { navigator.observeForPlanning(setOf(target), { screen(host) },
            { _, _ -> ActionOutcome(true, "Requested") }, { waits++ }) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(12, waits)
    }

    @Test fun stopDuringRestorationPreventsFurtherObservations() = runBlocking {
        val navigator = TaskAppNavigator(host).apply { rememberOpened(target) }
        var reads = 0
        val failure = runCatching { navigator.observeForPlanning(setOf(target), { reads++; screen(host) },
            { _, _ -> ActionOutcome(true, "Requested") }, { throw CancellationException("Stopped") }) }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(1, reads)
    }
}
