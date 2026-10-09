package com.example.handsfree

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import com.example.handsfree.ui.uiText
import org.junit.Rule
import org.junit.Test

class AppBrowserTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun missingApplicationShowsHelpfulMessage() {
        val appsLabel = compose.activity.uiText(R.string.your_apps)
        val searchLabel = compose.activity.uiText(R.string.search_apps)
        val missingLabel = compose.activity.uiText(R.string.no_match)
        compose.onAllNodes(androidx.compose.ui.test.hasText(appsLabel))[0].performClick()
        compose.onNodeWithText(searchLabel).assertIsDisplayed()
            .performTextInput("handsfree_nonexistent_application_92847")
        compose.waitUntil(15_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText(missingLabel))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(missingLabel).assertIsDisplayed()
    }
}
