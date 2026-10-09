package com.example.handsfree

import com.example.handsfree.agent.SpeechEndpoint
import com.example.handsfree.agent.ConversationCommand
import com.example.handsfree.agent.VoiceReply
import com.example.handsfree.agent.FoodIntake
import org.junit.Assert.*
import org.junit.Test

class SpeechEndpointTest {
    @Test fun kannadaOrderUsesFoodIntakeAndCartVerificationRoute() {
        assertTrue(FoodIntake.applies("ಎರಡು ದೋಸೆ ಬೇಕು"))
        assertEquals("com.application.zomato", FoodIntake.automaticPackage("ಜೊಮ್ಯಾಟೋ ಬಳಸಿ",
            listOf("com.application.zomato", "in.swiggy.android")))
    }
    @Test fun waitsFiveSecondsAfterSpeechNotAfterRecordingStarts() {
        val endpoint = SpeechEndpoint(0)
        assertFalse(endpoint.sample(100, 4000))
        assertFalse(endpoint.sample(200, 4000))
        assertFalse(endpoint.sample(1800, 0))
        assertFalse(endpoint.sample(5199, 0))
        assertTrue(endpoint.sample(5200, 0))
    }
    @Test fun continuedSpeechRestartsThePause() {
        val endpoint = SpeechEndpoint(0)
        endpoint.sample(100, 4000); endpoint.sample(200, 4000)
        endpoint.sample(4500, 4000); endpoint.sample(4600, 4000)
        assertFalse(endpoint.sample(5200, 0))
        assertTrue(endpoint.sample(9600, 0))
    }
    @Test fun isolatedNoiseDoesNotSubmitSilence() {
        val endpoint = SpeechEndpoint(0)
        endpoint.sample(100, 4000); endpoint.sample(200, 0)
        assertTrue(endpoint.sample(8000, 0))
        assertFalse(endpoint.hasSpeech)
    }
    @Test fun kannadaCommandsAndExplicitConsent() {
        assertEquals(ConversationCommand.NEW_TASK, ConversationCommand.parse("ಹೊಸ ಕೆಲಸ"))
        assertEquals(ConversationCommand.HOME, ConversationCommand.parse("ಮನೆಗೆ ಹೋಗು"))
        assertEquals(ConversationCommand.STOP, ConversationCommand.parse("ಮಾತುಕತೆ ನಿಲ್ಲಿಸಿ"))
        assertEquals(VoiceReply.APPROVE, VoiceReply.parse("ಅನುಮತಿ ನೀಡುತ್ತೇನೆ", false))
        assertEquals(VoiceReply.REJECT, VoiceReply.parse("ತಿರಸ್ಕರಿಸಿ", false))
        assertEquals(VoiceReply.UNCLEAR, VoiceReply.parse("ಹೌದು", false))
    }
}
