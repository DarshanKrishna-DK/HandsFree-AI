package com.example.handsfree

import com.example.handsfree.agent.AppGoalVerifier
import org.junit.Assert.*
import org.junit.Test

class AppGoalVerifierTest {
    @Test fun recognizesOnlyLaunchGoals() {
        assertTrue(AppGoalVerifier.isLaunchOnly("Please open Zomato.", "Zomato", "com.application.zomato"))
        assertFalse(AppGoalVerifier.isLaunchOnly("Open Zomato and find vegetarian food", "Zomato", "com.application.zomato"))
        assertFalse(AppGoalVerifier.isLaunchOnly("Open Zomato", "Maps", "com.google.android.apps.maps"))
    }
    @Test fun recognizesKannadaLaunchWithoutInventingAnApp() {
        assertTrue(AppGoalVerifier.isLaunchOnly("Zomato ತೆರೆಯಿರಿ", "Zomato", "com.application.zomato"))
    }
}
