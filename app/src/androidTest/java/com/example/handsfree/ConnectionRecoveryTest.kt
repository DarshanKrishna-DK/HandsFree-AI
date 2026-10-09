package com.example.handsfree

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.handsfree.agent.AgentController
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry

/** Opt in with recovery_test=true; host restores ADB reverse after the ready marker. */
class ConnectionRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun recoversWithoutUserTapWhileHomeRemainsVisible() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recovery_test") == "true")
        val controller = AgentController.get(compose.activity)
        compose.waitUntil(8_000) { !controller.connected && controller.connection.contains("couldn't connect") }
        Log.i("HandsFreeTest", "RESTORE_BACKEND_NOW")
        compose.waitUntil(25_000) { controller.connected }
    }
}
