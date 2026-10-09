package com.example.handsfree
import com.example.handsfree.agent.VoicePolicy
import org.junit.Assert.*
import org.junit.Test
class VoicePolicyTest {
    @Test fun typingModeAndUnacceptedDisclosureNeverSendSpeech() {
        assertFalse(VoicePolicy.maySpeak(false, true))
        assertFalse(VoicePolicy.maySpeak(true, false))
        assertFalse(VoicePolicy.maySpeak(false, false))
        assertTrue(VoicePolicy.maySpeak(true, true))
    }
}
