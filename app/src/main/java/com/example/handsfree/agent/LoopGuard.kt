package com.example.handsfree.agent

class LoopGuard {
    private var steps = 0
    private var stalePlans = 0
    private val repeats = mutableMapOf<String, Int>()
    fun checkFreshness(fresh: Boolean): String? {
        stalePlans = if (fresh) 0 else stalePlans + 1
        return if (stalePlans >= 3)
            "The screen keeps changing during reasoning. Open a stable search or results screen manually, then start again. No stale action was executed."
        else null
    }
    fun check(action: String, observationId: String): String? {
        if (++steps > 40) return "Action limit reached. Review the task and start again."
        val key = "$observationId|$action"
        repeats[key] = (repeats[key] ?: 0) + 1
        return if (repeats.getValue(key) > 2) "Repeated action on an unchanged screen. Please review before retrying." else null
    }
}
