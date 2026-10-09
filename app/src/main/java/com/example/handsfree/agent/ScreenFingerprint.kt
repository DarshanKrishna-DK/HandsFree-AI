package com.example.handsfree.agent

/** Identity for node actions; display geometry remains available in observations. */
@ConsistentCopyVisibility
data class ScreenFingerprint private constructor(
    private val packageName: String,
    private val windowId: Int?,
    private val blockedReason: String?,
    private val elements: List<ScreenElement>,
) {
    companion object {
        fun from(packageName: String, windowId: Int?, blockedReason: String?, elements: List<ScreenElement>) =
            // Execution uses freshly retrieved AccessibilityNodeInfo handles, never
            // these coordinates. Keep all semantic fields and element order exact.
            ScreenFingerprint(packageName, windowId, blockedReason, elements.map { it.copy(bounds = emptyList()) })
    }
}
