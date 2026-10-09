package com.example.handsfree
import com.example.handsfree.agent.VoiceReply
import org.junit.Assert.assertEquals
import org.junit.Test
class VoiceReplyTest {
    @Test fun approvalNeedsAnExplicitCommand() {
        assertEquals(VoiceReply.APPROVE, VoiceReply.parse("I approve.", false))
        assertEquals(VoiceReply.UNCLEAR, VoiceReply.parse("I think yes but not sure", false))
        assertEquals(VoiceReply.UNCLEAR, VoiceReply.parse("yes", false))
        assertEquals(VoiceReply.REJECT, VoiceReply.parse("No", false))
    }
    @Test fun foodAnswerIsNotAnApproval() {
        assertEquals(VoiceReply.ANSWER, VoiceReply.parse("something vegetarian", true))
        assertEquals(VoiceReply.STOP, VoiceReply.parse("Stop!", true))
    }
}
