package com.example.handsfree.agent

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Session-only app context. Android still owns launches, observation and approvals. */
class TaskAppNavigator(private val hostPackage: String) {
    var taskPackage: String? = null
        private set

    fun rememberOpened(packageName: String) { taskPackage = packageName }

    suspend fun observeForPlanning(
        approvedPackages: Set<String>,
        observe: () -> ScreenObservation,
        open: (String, ScreenObservation) -> ActionOutcome,
        settle: suspend () -> Unit,
    ): ScreenObservation {
        currentCoroutineContext().ensureActive()
        val before = observe()
        val target = taskPackage ?: return before
        // Only return from our own clarification UI. Never interrupt another app,
        // bypass a privacy stop, guess a destination or reuse revoked approval.
        if (before.blockedReason != null || before.packageName != hostPackage || target !in approvedPackages)
            return before
        currentCoroutineContext().ensureActive()
        check(open(target, before).accepted) {
            "I couldn't return to the selected app. Open it, then try again."
        }
        repeat(12) {
            settle()
            currentCoroutineContext().ensureActive()
            val fresh = observe()
            if (fresh.blockedReason != null || fresh.packageName == target) return fresh
        }
        error("The selected app hasn't opened yet. Open it, then try again. No screen action was taken.")
    }
}
