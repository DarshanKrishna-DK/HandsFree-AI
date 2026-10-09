package com.example.handsfree.agent

data class ScreenElement(
    val id: String, val text: String, val description: String, val role: String,
    val actions: List<String>, val bounds: List<Int>, val editable: Boolean = false,
)
data class ScreenObservation(
    val id: String, val packageName: String, val elements: List<ScreenElement>,
    val blockedReason: String? = null,
)
data class ActionArguments(
    val packageName: String? = null, val text: String? = null,
    val direction: String? = null, val message: String? = null,
    val expectedText: String? = null, val evidenceIds: List<String> = emptyList(),
    val cartItemIds: List<String> = emptyList(), val cartTotalIds: List<String> = emptyList(),
) {
    fun supplied(): Set<String> = buildSet {
        if (packageName != null) add("package_name")
        if (text != null) add("text")
        if (direction != null) add("direction")
        if (message != null) add("message")
        if (expectedText != null) add("expected_text")
        if (evidenceIds.isNotEmpty()) add("evidence_ids")
        if (cartItemIds.isNotEmpty()) add("cart_item_ids")
        if (cartTotalIds.isNotEmpty()) add("cart_total_ids")
    }
}
data class ProposedAction(
    val action: String, val observationId: String, val targetId: String? = null,
    val arguments: ActionArguments = ActionArguments(), val reason: String = "",
    val requiresConfirmation: Boolean = false,
)
enum class AgentStatus { IDLE, UNDERSTANDING, OBSERVING, PLANNING, EXECUTING, VERIFYING,
    WAITING_FOR_USER, COMPLETED, FAILED, STOPPED }
data class ActionRecord(val action: String, val result: String)
data class ActionOutcome(val accepted: Boolean, val message: String)
