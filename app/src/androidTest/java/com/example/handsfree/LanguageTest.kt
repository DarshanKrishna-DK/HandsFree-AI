package com.example.handsfree

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.handsfree.agent.VoiceController
import com.example.handsfree.ui.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LanguageTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun homeSwitchChangesInterfaceVoiceAndSurvivesRecreation() {
        compose.onNodeWithText("ಕನ್ನಡ").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("ಮುಖಪುಟ")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("ಮುಖಪುಟ").assertIsDisplayed()
        compose.onNodeWithText("ಮಾತುಕತೆ ಪ್ರಾರಂಭಿಸಿ").assertIsDisplayed()
        assertEquals("kn-IN", VoiceController.get(compose.activity).language)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("ಮುಖಪುಟ")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("kn-IN", AppLanguage.code(compose.activity))
        compose.onNodeWithText("English").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Home")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Start conversation").assertIsDisplayed()
        assertEquals("en-IN", VoiceController.get(compose.activity).language)
    }
}
