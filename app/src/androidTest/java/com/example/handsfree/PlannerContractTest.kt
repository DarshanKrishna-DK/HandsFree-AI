package com.example.handsfree

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.handsfree.agent.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against Android's actual JSON parser; no permissive JVM substitute. */
@RunWith(AndroidJUnit4::class)
class PlannerContractTest {
    private fun proposal() = JSONObject().put("action", "ask_user").put("observation_id", "screen-1")
        .put("reason", "Need your choice").put("requires_confirmation", false)
        .put("arguments", JSONObject().put("message", "Which meal?"))

    private fun rejected(content: String) {
        try { ActionProposalCodec.decode(content, "screen-1"); fail("Accepted invalid model output") }
        catch (_: Exception) { /* Invalid model output never reaches execution. */ }
    }

    @Test fun validProposalPreservesLanguageAndDoesNotBecomeConsent() {
        val source = proposal().put("arguments", JSONObject().put("message", "ಯಾವ ಊಟ? कौन सा खाना?"))
        val action = ActionProposalCodec.decode(source.toString(), "screen-1")
        assertEquals("ಯಾವ ಊಟ? कौन सा खाना?", action.arguments.message)
        assertFalse(action.requiresConfirmation)
        assertEquals("ask_user", action.action)
    }

    @Test fun rejectsCoercedTypesAndMissingRequiredFields() {
        rejected(proposal().put("requires_confirmation", "false").toString())
        rejected(proposal().put("reason", 42).toString())
        rejected(proposal().put("target_id", false).toString())
        rejected(proposal().put("arguments", "{}").toString())
        rejected(proposal().put("arguments", JSONObject.NULL).toString())
        for (key in listOf("action", "observation_id", "reason", "requires_confirmation")) {
            rejected(proposal().apply { remove(key) }.toString())
        }
    }

    @Test fun rejectsAmbiguousOrNonJsonOutput() {
        rejected(proposal().toString().dropLast(1) + ",\"action\":\"click\"}")
        rejected(proposal().toString() + " {}")
        rejected("```json\n${proposal()}\n```")
        rejected(proposal().toString().replace("\"action\":", "action:"))
        rejected(proposal().toString().replace("\"action\"", "'action'"))
        rejected("[]")
    }

    @Test fun rejectsUnknownFieldsActionsAndStaleObservation() {
        rejected(proposal().put("shell", "ignored command").toString())
        rejected(proposal().put("action", "execute_shell").toString())
        rejected(proposal().put("observation_id", "screen-old").toString())
        rejected(proposal().put("arguments", JSONObject().put("script", "code")).toString())
    }

    @Test fun rejectsInvalidOrExcessiveEvidenceAndText() {
        for (value in listOf<Any>("e1", JSONArray().put(1), JSONArray().put("e1").put("e1"),
            JSONArray().put(""), JSONObject.NULL, JSONArray((1..11).map { "e$it" }))) {
            rejected(proposal().put("arguments", JSONObject().put("evidence_ids", value)).toString())
        }
        rejected(proposal().put("arguments", JSONObject().put("cart_total_ids", JSONArray(listOf("a", "b", "c", "d")))).toString())
        rejected(proposal().put("arguments", JSONObject().put("message", "x".repeat(1001))).toString())
        rejected(proposal().put("reason", " ").toString())
        rejected(" ".repeat(16_385))
    }

    @Test fun validClickStillRequiresPolicyConsentAndProtectedScreenIsDenied() {
        val action = ActionProposalCodec.decode(proposal().put("action", "click").put("target_id", "e1")
            .put("arguments", JSONObject()).toString(), "screen-1")
        val screen = ScreenObservation("screen-1", "example.food", listOf(
            ScreenElement("e1", "View menu", "", "Button", listOf("click"), listOf(0, 0, 100, 100))))
        val policy = SafetyPolicyEngine()
        assertEquals(Disposition.CONFIRM, policy.evaluate(action, screen, setOf("example.food")).disposition)
        assertEquals(Disposition.DENY, policy.evaluate(action, screen.copy(blockedReason = "Private screen"), setOf("example.food")).disposition)
        assertEquals(Disposition.DENY, policy.evaluate(action, screen.copy(id = "screen-2"), setOf("example.food")).disposition)
    }

    @Test fun localAndDevelopmentPlannersReceiveSameBoundedObservation() {
        val request = PlannerRequest("task", "Find lunch", ScreenObservation("screen-1", "example.food", emptyList(), "Private"),
            emptyList(), (1..40).map { ActionRecord("read_screen", "x".repeat(600)) }, (1..30).map { "preference $it" }, "kn-IN")
        val json = PlannerProtocol.encode(request)
        assertEquals("kn-IN", json.getString("response_language"))
        assertEquals("screen-1", json.getString("observation_id"))
        assertEquals(listOf("ask_user", "stop_task"), (0..1).map { json.getJSONArray("allowed_actions").getString(it) })
        assertEquals(30, json.getJSONArray("history").length())
        assertEquals(500, json.getJSONArray("history").getJSONObject(0).getString("result").length)
        assertEquals(20, json.getJSONArray("constraints").length())
    }
}
