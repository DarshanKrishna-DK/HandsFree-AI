package com.example.handsfree

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class AppBrowserTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun missingApplicationShowsHelpfulMessage() {
        compose.onAllNodes(androidx.compose.ui.test.hasText("Your apps"))[0].performClick()
        compose.onNodeWithText("Search apps").assertIsDisplayed()
            .performTextInput("handsfree_nonexistent_application_92847")
        compose.waitUntil(15_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("No matching apps"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("No matching apps").assertIsDisplayed()
    }
}
