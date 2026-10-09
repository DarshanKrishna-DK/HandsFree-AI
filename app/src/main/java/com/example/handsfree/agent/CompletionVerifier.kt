package com.example.handsfree.agent

import java.math.BigDecimal
import java.math.MathContext

object CompletionVerifier {
    fun expectedCalculation(goal: String): BigDecimal? {
        // Only a completely recognized single expression can be auto-certified.
        val match = Regex("(?i)\\s*(?:please\\s+)?(?:open\\s+calculator\\s+and\\s+)?(?:calculate\\s+)?(-?\\d+(?:\\.\\d+)?)\\s*([+*×/÷−-])\\s*(-?\\d+(?:\\.\\d+)?)(?:\\s+in\\s+calculator)?\\s*[.!?]?\\s*")
            .matchEntire(goal) ?: return null
        val a = match.groupValues[1].toBigDecimalOrNull() ?: return null
        val b = match.groupValues[3].toBigDecimalOrNull() ?: return null
        return try {
            when (match.groupValues[2]) {
                "+" -> a + b; "-", "−" -> a - b; "*", "×" -> a * b
                else -> a.divide(b, MathContext.DECIMAL64)
            }
        } catch (_: ArithmeticException) { null }
    }

    fun verified(goal: String, screen: ScreenObservation, args: ActionArguments): Boolean {
        val expected = args.expectedText?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val evidence = screen.elements.filter { it.id in args.evidenceIds }
        if (evidence.isEmpty() || evidence.size != args.evidenceIds.distinct().size || screen.blockedReason != null) return false
        val calculation = expectedCalculation(goal)
        if (screen.packageName in SafetyPolicyEngine.calculatorPackages && calculation != null) {
            val proposed = expected.replace(",", "").toBigDecimalOrNull() ?: return false
            return proposed.compareTo(calculation) == 0 && evidence.any { element ->
                element.actions.isEmpty() && listOf(element.text, element.description).any {
                    it.trim().removePrefix("=").trim().replace(",", "").toBigDecimalOrNull()?.compareTo(calculation) == 0
                }
            }
        }
        return evidence.any { it.text.trim().equals(expected, true) || it.description.trim().equals(expected, true) }
    }
}
