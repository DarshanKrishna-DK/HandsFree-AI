package com.example.handsfree

import com.example.handsfree.agent.*
import org.junit.Assert.*
import org.junit.Test

class SafetyPolicyTest {
    private val node = ScreenElement("e1", "2", "", "Button", listOf("click"), listOf(0, 0, 100, 100))
    private val screen = ScreenObservation("obs1", "com.oneplus.calculator", listOf(node))
    private val policy = SafetyPolicyEngine()
    private fun check(action: ProposedAction, snapshot: ScreenObservation = screen) =
        policy.evaluate(action, snapshot, setOf("com.oneplus.calculator"))

    @Test fun calculatorDigitIsAllowed() {
        assertEquals(Disposition.ALLOW, check(ProposedAction("click", "obs1", "e1")).disposition)
    }
    @Test fun staleAndMissingTargetsAreDenied() {
        assertEquals(Disposition.DENY, check(ProposedAction("click", "old", "e1")).disposition)
        assertEquals(Disposition.DENY, check(ProposedAction("click", "obs1", "gone")).disposition)
    }
    @Test fun unknownActionsAndUnsupportedArgumentsAreDenied() {
        assertEquals(Disposition.DENY, check(ProposedAction("shell", "obs1")).disposition)
        assertEquals(Disposition.DENY, check(ProposedAction("click", "obs1", "e1",
            ActionArguments(text = "unexpected"))).disposition)
    }
    @Test fun paymentAndPasswordCannotBeApproved() {
        val payment = screen.copy(elements = listOf(node.copy(text = "Confirm payment")))
        assertEquals(Disposition.DENY, check(ProposedAction("click", "obs1", "e1"), payment).disposition)
        assertEquals(Disposition.DENY, check(ProposedAction("click", "obs1", "e1"),
            screen.copy(blockedReason = "Protected input")).disposition)
    }
    @Test fun stopWorksEvenWithStaleObservation() {
        assertEquals(Disposition.ALLOW, check(ProposedAction("stop_task", "old")).disposition)
    }
    @Test fun appSwitchAndStoreAlwaysNeedConsent() {
        assertEquals(Disposition.CONFIRM, check(ProposedAction("open_app", "obs1",
            arguments = ActionArguments(packageName = "com.android.settings"))).disposition)
        assertEquals(Disposition.CONFIRM, check(ProposedAction("open_play_store", "obs1",
            arguments = ActionArguments(packageName = "com.example.food"))).disposition)
    }
    @Test fun completionNeedsObservedEvidence() {
        assertFalse(CompletionVerifier.verified("Calculate 250 + 150", screen,
            ActionArguments(expectedText = "400", evidenceIds = listOf("e1"))))
        val result = screen.copy(elements = listOf(node.copy(text = "400", actions = emptyList())))
        assertTrue(CompletionVerifier.verified("Calculate 250 + 150", result,
            ActionArguments(expectedText = "400", evidenceIds = listOf("e1"))))
        assertFalse(CompletionVerifier.verified("Calculate 250 + 150", result,
            ActionArguments(expectedText = "40", evidenceIds = listOf("e1"))))
    }
    @Test fun recommendationsNeedObservedEvidence() {
        assertEquals(Disposition.DENY, check(ProposedAction("speak", "obs1",
            arguments = ActionArguments(message = "An invented restaurant rating"))).disposition)
    }
    @Test fun compoundCalculationRequiresHumanReview() {
        assertNull(CompletionVerifier.expectedCalculation("Calculate 250 + 150 then subtract 100"))
        assertNull(CompletionVerifier.expectedCalculation("Calculate 250 + 150%"))
        assertNotNull(CompletionVerifier.expectedCalculation("Open Calculator and calculate 250 + 150"))
    }
    @Test fun protectedDescendantBlocksUnlabelledParent() {
        val payment = screen.copy(elements = listOf(node.copy(text = ""), node.copy(id = "e2", text = "Pay now", actions = emptyList())))
        assertEquals(Disposition.DENY, check(ProposedAction("click", "obs1", "e1"), payment).disposition)
        assertTrue(SafetyPolicyEngine.isProtected("Enter CVV"))
        assertTrue(SafetyPolicyEngine.isProtected("Enter PIN"))
    }
    @Test fun calculatorDisplayReplacementIsRejected() {
        val editable = screen.copy(elements = listOf(node.copy(editable = true, actions = listOf("type_text"))))
        assertEquals(Disposition.DENY, check(ProposedAction("type_text", "obs1", "e1",
            ActionArguments(text = "250+150")), editable).disposition)
    }
    @Test fun cartTotalsCanBeReadWithoutAuthorizingPayment() {
        val cart = screen.copy(elements = listOf(node.copy(text = "Pay now"),
            node.copy(id = "e2", text = "Grand total ₹320", actions = emptyList())))
        assertEquals(Disposition.ALLOW, check(ProposedAction("read_screen", "obs1"), cart).disposition)
        assertEquals(Disposition.ALLOW, check(ProposedAction("speak", "obs1",
            arguments = ActionArguments(message = "Cart total", evidenceIds = listOf("e2"))), cart).disposition)
        assertEquals(Disposition.DENY, check(ProposedAction("click", "obs1", "e1"), cart).disposition)
    }
    @Test fun paymentVariantsStayBlockedEvenWithConfirmation() {
        for (label in listOf("Pay", "Pay ₹320", "Proceed to payment", "Buy now", "Confirm order", "Tap to pay")) {
            val payment = screen.copy(elements = listOf(node.copy(text = label)))
            assertEquals(label, Disposition.DENY, check(ProposedAction("click", "obs1", "e1", requiresConfirmation = true), payment).disposition)
            assertEquals(Disposition.ALLOW, check(ProposedAction("read_screen", "obs1"), payment).disposition)
        }
        val describedPayment = screen.copy(elements = listOf(node.copy(text = "₹320", description = "Pay")))
        assertEquals(Disposition.DENY, check(ProposedAction("click", "obs1", "e1"), describedPayment).disposition)
    }
}
