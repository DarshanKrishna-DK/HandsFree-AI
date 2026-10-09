package com.example.handsfree

import com.example.handsfree.agent.*
import org.junit.Assert.*
import org.junit.Test

class ScreenFingerprintTest {
    private val node = ScreenElement("e0", "Samosa 50", "", "Button", listOf("click"), listOf(0, 0, 200, 60))
    private fun fingerprint(elements: List<ScreenElement> = listOf(node), pkg: String = "food.app",
                            window: Int = 7, blocked: String? = null) = ScreenFingerprint.from(pkg, window, blocked, elements)

    @Test fun animationOnlyMovementDoesNotInvalidateNodeActionObservation() {
        assertEquals(fingerprint(), fingerprint(listOf(node.copy(bounds = listOf(1, 0, 201, 60)))))
    }
    @Test fun pricesLabelsRolesAndSupportedActionsInvalidatePlans() {
        for (changed in listOf(node.copy(text = "Samosa 60"), node.copy(description = "Pay now"),
            node.copy(role = "EditText"), node.copy(actions = emptyList()), node.copy(editable = true))) {
            assertNotEquals(fingerprint(), fingerprint(listOf(changed)))
        }
    }
    @Test fun AppWindowAndPrivacyChangesInvalidatePlans() {
        assertNotEquals(fingerprint(), fingerprint(pkg = "other.app"))
        assertNotEquals(fingerprint(), fingerprint(window = 8))
        assertNotEquals(fingerprint(), fingerprint(blocked = "Protected"))
    }
    @Test fun TargetRemovalIdentityAndOrderChangesInvalidatePlans() {
        assertNotEquals(fingerprint(), fingerprint(emptyList()))
        assertNotEquals(fingerprint(), fingerprint(listOf(node.copy(id = "e1"))))
        val other = node.copy(id = "e1", text = "Cancel")
        assertNotEquals(fingerprint(listOf(node, other)), fingerprint(listOf(other, node)))
    }
}
