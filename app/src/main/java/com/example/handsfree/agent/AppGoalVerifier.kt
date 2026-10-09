package com.example.handsfree.agent

object AppGoalVerifier {
    fun isLaunchOnly(goal: String, appName: String, packageName: String): Boolean {
        val app = "(?:${Regex.escape(appName)}|${Regex.escape(packageName)})"
        return Regex("(?i)\\s*(?:please\\s+)?(?:open|launch|start)\\s+$app\\s*[.!]?\\s*").matches(goal) ||
            Regex("\\s*$app\\s+ತೆರೆಯಿರಿ\\s*[.!]?\\s*", RegexOption.IGNORE_CASE).matches(goal)
    }
}
