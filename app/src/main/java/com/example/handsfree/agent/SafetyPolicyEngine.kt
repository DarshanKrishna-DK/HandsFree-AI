package com.example.handsfree.agent

enum class Disposition { ALLOW, CONFIRM, DENY }
data class PolicyDecision(val disposition: Disposition, val message: String = "")

class SafetyPolicyEngine {
    companion object {
        val actions = setOf("open_app", "read_screen", "click", "type_text", "scroll", "go_back",
            "ask_user", "open_play_store", "speak", "finish_task", "stop_task")
        val calculatorPackages = setOf("com.oneplus.calculator", "com.google.android.calculator", "com.android.calculator2")
        private val authentication = Regex("(?i)\\b(password|passcode|otp|one.time password|pin|cvv|banking|verification code|card number|account number|expiry date)\\b")
        private val payment = Regex("(?i)\\b(pay|confirm payment|place order|checkout|proceed to payment|make payment|complete purchase|buy now|confirm order)\\b")
        fun isAuthentication(text: String) = authentication.containsMatchIn(text)
        fun isPayment(text: String) = payment.containsMatchIn(text)
        fun isProtected(text: String) = isAuthentication(text) || isPayment(text)
        private val parameters = mapOf(
            "open_app" to setOf("package_name"), "read_screen" to emptySet(), "click" to emptySet(),
            "type_text" to setOf("text"), "scroll" to setOf("direction"), "go_back" to emptySet(),
            "ask_user" to setOf("message"), "open_play_store" to setOf("package_name"),
            "speak" to setOf("message", "evidence_ids"), "finish_task" to setOf("message", "expected_text", "evidence_ids", "cart_item_ids", "cart_total_ids"),
            "stop_task" to setOf("message"))
    }

    fun evaluate(action: ProposedAction, screen: ScreenObservation, approvedApps: Set<String>): PolicyDecision {
        fun deny(message: String) = PolicyDecision(Disposition.DENY, message)
        if (action.action !in actions) return deny("Unknown action")
        if (action.arguments.supplied().any { it !in parameters.getValue(action.action) }) return deny("Unsupported parameters")
        if (action.action == "stop_task") return PolicyDecision(Disposition.ALLOW)
        if (action.observationId != screen.id) return deny("The screen changed. Observe again.")
        if (screen.blockedReason != null && action.action !in setOf("ask_user", "stop_task")) return deny(screen.blockedReason)
        if (screen.elements.any { isAuthentication(it.text + " " + it.description) } && action.action !in setOf("ask_user", "stop_task"))
            return deny("Protected screen stays under your control")
        if (screen.elements.any { isPayment(it.text + " " + it.description) } &&
            action.action in setOf("click", "type_text")) return deny("Payment controls stay under your control. I can read the cart total.")
        val node = screen.elements.find { it.id == action.targetId }
        if (action.action in setOf("click", "type_text", "scroll")) {
            if (node == null || action.action !in node.actions) return deny("Target is missing or does not support this action")
            if (screen.packageName !in approvedApps) return deny("Application is outside the approved task scope")
            if (isProtected(node.text + " " + node.description)) return deny("Protected action stays under your control")
            if (action.action == "type_text" && (!node.editable || action.arguments.text == null ||
                    isProtected(action.arguments.text))) return deny("Text entry is not permitted here")
            if (action.action == "type_text" && screen.packageName in calculatorPackages)
                return deny("Use Calculator keys; replacing its display does not reliably evaluate an expression")
            if (action.action == "scroll" && action.arguments.direction !in setOf("forward", "backward")) return deny("Invalid scroll direction")
        } else if (action.targetId != null) return deny("Unexpected target")
        if (action.arguments.evidenceIds.any { id -> screen.elements.none { it.id == id } }) return deny("Evidence is not on this screen")
        if ((action.arguments.cartItemIds + action.arguments.cartTotalIds).any { it !in action.arguments.evidenceIds })
            return deny("Cart details must reference the current evidence")
        if (action.action in setOf("open_app", "open_play_store")) {
            val packageName = action.arguments.packageName
            if (packageName == null || !Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+").matches(packageName)) return deny("Invalid package identifier")
            if (action.action == "open_play_store" || packageName !in approvedApps)
                return PolicyDecision(Disposition.CONFIRM, "Open ${if (action.action == "open_play_store") "the Play Store listing for " else ""}$packageName?")
        }
        if (action.action == "go_back" && screen.packageName !in approvedApps) return deny("Current app is outside the approved scope")
        if (action.action == "finish_task" && (action.arguments.evidenceIds.isEmpty() || action.arguments.expectedText.isNullOrBlank()))
            return deny("Completion requires visible evidence")
        if (action.action == "speak" && action.arguments.evidenceIds.isEmpty()) return deny("Recommendations require visible evidence")
        if (action.action in setOf("ask_user", "speak", "finish_task") && action.arguments.message.isNullOrBlank()) return deny("Missing message")
        val calculatorClick = action.action == "click" && screen.packageName in calculatorPackages && node != null &&
            listOf(node.text.trim().lowercase(), node.description.trim().lowercase()).any {
                it.matches(Regex("[0-9]{1,2}|[.+×÷*/=−%-]")) || it in setOf("clear", "all clear", "backspace", "delete", "add", "plus", "subtract", "minus", "multiply", "divide", "equals", "point", "percentage sign")
            }
        if ((action.action == "click" && !calculatorClick) || action.action == "type_text" ||
            (action.requiresConfirmation && action.action != "open_app")) {
            if (action.action == "type_text") return PolicyDecision(Disposition.CONFIRM,
                "Enter this text: ${action.arguments.text}\nInto: ${node?.text?.ifBlank { node.description }}")
            return PolicyDecision(Disposition.CONFIRM, "${action.action}: ${node?.text?.ifBlank { node.description } ?: action.reason}")
        }
        return PolicyDecision(Disposition.ALLOW)
    }
}
