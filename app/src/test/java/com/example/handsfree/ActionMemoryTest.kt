package com.example.handsfree

import com.example.handsfree.agent.*
import org.junit.Assert.*
import org.junit.Test

class ActionMemoryTest {
    private val editor = ScreenElement("e2", "vegetarian samosas", "Search", "EditText",
        listOf("type_text"), emptyList(), true)
    private val screen = ScreenObservation("s", "food.app", listOf(editor))
    private val action = ProposedAction("type_text", "s", "e2", ActionArguments(text = "vegetarian samosas"))

    @Test fun visibleExactTextIsRejectedBeforeAnotherConsent() {
        val decision = SafetyPolicyEngine().evaluate(action, screen, setOf("food.app"))
        assertEquals(Disposition.DENY, decision.disposition)
        assertTrue(decision.message.contains("already"))
    }

    @Test fun differentQueryStillRequiresConsent() {
        assertEquals(Disposition.CONFIRM, SafetyPolicyEngine().evaluate(
            action.copy(arguments = ActionArguments(text = "vegetarian sandwich")), screen, setOf("food.app")).disposition)
    }

    @Test fun historyRetainsEnteredTextWithoutClaimingSuccess() {
        val result = ActionMemory.executionResult(action, screen, ActionOutcome(true, "Android accepted type_text; observing next."))
        assertTrue(result.contains("vegetarian samosas"))
        assertTrue(result.contains("Android accepted"))
        assertFalse(result.contains("verified"))
        val failed = ActionMemory.executionResult(action, screen, ActionOutcome(false, "Android could not perform type_text."))
        assertTrue(failed.contains("could not"))
    }

    @Test fun observationDoesNotReplacePreviousActionResult() {
        val history = listOf(ActionRecord("type_text", "Android accepted query vegetarian samosas"),
            ActionRecord("observation", "Screen changed"))
        val previous = ActionMemory.previousResult(history)!!
        assertTrue(previous.contains("vegetarian samosas"))
        assertTrue(previous.contains("Screen changed"))
        assertNull(ActionMemory.previousResult(emptyList()))
    }
}
