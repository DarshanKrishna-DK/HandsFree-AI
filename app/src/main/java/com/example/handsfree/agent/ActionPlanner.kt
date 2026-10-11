package com.example.handsfree.agent

import com.example.handsfree.apps.LaunchableApp

/** Engines propose actions; the controller and accessibility service still own consent and execution. */
fun interface ActionPlanner {
    suspend fun nextAction(request: PlannerRequest): ProposedAction
}

data class PlannerRequest(
    val taskId: String,
    val goal: String,
    val screen: ScreenObservation,
    val apps: List<LaunchableApp>,
    val history: List<ActionRecord>,
    val constraints: List<String>,
    val responseLanguage: String = "en-IN",
)
