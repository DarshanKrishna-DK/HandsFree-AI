package com.example.handsfree

import android.media.AudioManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.handsfree.agent.VoiceController
import com.example.handsfree.ui.uiText
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Explicit live test: real backend, credentials, existing user permission and disclosure. */
class VoicePlaybackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun kannadaResponsePlaysAndFinishes() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("live_voice_test") == "true")
        val voice = VoiceController.get(compose.activity)
        val audio = compose.activity.getSystemService(AudioManager::class.java)
        assertTrue("Microphone permission must be granted manually", voice.hasPermission())
        assertTrue("Audio disclosure must be accepted manually",
            compose.activity.getSharedPreferences("conversation", 0).getBoolean("audio_disclosure", false))
        compose.onNodeWithText("ಕನ್ನಡ").performClick()
        compose.waitUntil(10_000) { voice.language == "kn-IN" }
        try {
            assertFalse("Pause other media before this playback test", audio.isMusicActive)
            compose.runOnIdle {
                voice.conversationEnabled = true
                assertTrue(voice.beginSession())
                voice.speak("ನಮಸ್ಕಾರ. ನಿಮಗೆ ಯಾವ ತಿಂಡಿ ಬೇಕು?")
                assertTrue(voice.speaking)
            }
            compose.waitUntil(30_000) { audio.isMusicActive }
            compose.waitUntil(35_000) { !voice.speaking }
            compose.runOnIdle {
                assertNotEquals(compose.activity.uiText(R.string.audio_unavailable), voice.message)
                assertTrue("Conversation must survive playback", voice.sessionActive)
            }
        } finally { compose.runOnIdle { voice.endSession() } }
    }
}
