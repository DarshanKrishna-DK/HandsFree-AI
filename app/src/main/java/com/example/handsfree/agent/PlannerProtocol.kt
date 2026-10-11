package com.example.handsfree.agent

import org.json.JSONArray
import org.json.JSONObject

/** Same bounded observation representation for local and development planners. */
object PlannerProtocol {
    fun encode(request: PlannerRequest): JSONObject {
        val (taskId, goal, screen, apps, history, constraints, responseLanguage) = request
        return JSONObject().put("task_id", taskId).put("goal", goal)
            .put("response_language", responseLanguage)
            .put("observation_id", screen.id).put("task_state", "PLANNING")
            .put("screen", JSONObject().put("package_name", screen.packageName)
                .put("blocked_reason", screen.blockedReason ?: JSONObject.NULL)
                .put("elements", JSONArray(screen.elements.map { e -> JSONObject()
                    .put("id", e.id).put("text", e.text).put("description", e.description).put("role", e.role)
                    .put("actions", JSONArray(e.actions)).put("bounds", JSONArray(e.bounds)).put("editable", e.editable) })))
            .put("available_apps", JSONArray(apps.take(500).map { JSONObject().put("package_name", it.packageName).put("name", it.name.take(200)) }))
            .put("allowed_actions", JSONArray(if (screen.blockedReason != null) listOf("ask_user", "stop_task") else SafetyPolicyEngine.actions.toList()))
            .put("history", JSONArray(history.takeLast(30).map { JSONObject().put("action", it.action).put("result", it.result.take(500)) }))
            .put("constraints", JSONArray(constraints.takeLast(20)))
            .put("previous_action_result", ActionMemory.previousResult(history) ?: JSONObject.NULL)
    }
}
