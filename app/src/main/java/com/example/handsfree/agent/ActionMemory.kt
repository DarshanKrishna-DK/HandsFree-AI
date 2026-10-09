package com.example.handsfree.agent

/** Short task-local receipts. Android acceptance alone is not verified completion. */
object ActionMemory {
    fun executionResult(action: ProposedAction, screen: ScreenObservation, outcome: ActionOutcome): String {
        val input = if (action.action == "type_text") " Requested text: ${action.arguments.text.orEmpty().take(180)}." else ""
        val target = screen.elements.find { it.id == action.targetId }
        val detail = target?.let { " Target ${it.id}: ${it.text.ifBlank { it.description }.take(100)}." }
            ?: action.arguments.packageName?.let { " Package: $it." }.orEmpty()
        return (outcome.message + input + detail).take(500)
    }

    fun previousResult(history: List<ActionRecord>): String? {
        val last = history.lastOrNull() ?: return null
        if (last.action != "observation") return "${last.action}: ${last.result}".take(1000)
        val executed = history.dropLast(1).lastOrNull { it.action != "observation" }
        return listOfNotNull(executed?.let { "${it.action}: ${it.result}" }, "Observation: ${last.result}")
            .joinToString("\n").take(1000)
    }
}
