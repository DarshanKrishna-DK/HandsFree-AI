package com.example.handsfree.agent

import com.example.handsfree.BuildConfig
import com.example.handsfree.apps.LaunchableApp
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BackendClient {
    private val http = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(130, TimeUnit.SECONDS).callTimeout(135, TimeUnit.SECONDS).build()
    private val base = "http://127.0.0.1:8000"

    suspend fun health(): JSONObject = JSONObject(request("/health"))

    suspend fun nextAction(taskId: String, goal: String, screen: ScreenObservation,
                           apps: List<LaunchableApp>, history: List<ActionRecord>, constraints: List<String>): ProposedAction {
        val payload = JSONObject().put("task_id", taskId).put("goal", goal)
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
            .put("previous_action_result", history.lastOrNull()?.result ?: JSONObject.NULL)
        return decode(JSONObject(request("/agent/next-action", payload.toString().toRequestBody("application/json".toMediaType()))))
    }

    suspend fun request(path: String, body: RequestBody? = null): String {
        check(BuildConfig.DEBUG) { "The local development backend is available only in debug builds." }
        val request = Request.Builder().url(base + path).apply { if (body != null) post(body) }.build()
        return suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(IOException("I couldn't connect right now. Check your connection and try again.", error))
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            val content = it.body?.string().orEmpty()
                            if (!it.isSuccessful) {
                                val detail = runCatching { JSONObject(content).optString("detail") }.getOrNull()
                                throw IOException(detail?.take(300)?.ifBlank { null } ?: "Backend error ${it.code}")
                            }
                            if (continuation.isActive) continuation.resume(content)
                        } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                    }
                }
            })
        }
    }

    private fun decode(json: JSONObject): ProposedAction {
        require(json.keys().asSequence().all { it in setOf("action", "target_id", "arguments", "reason", "observation_id", "requires_confirmation") })
        fun JSONObject.optional(key: String): String? = if (isNull(key)) null else getString(key)
        val args = json.getJSONObject("arguments")
        require(args.keys().asSequence().all { it in setOf("package_name", "text", "direction", "message", "expected_text", "evidence_ids", "cart_item_ids", "cart_total_ids") })
        fun ids(key: String): List<String> {
            val values = args.optJSONArray(key) ?: JSONArray()
            require(values.length() <= 10)
            return (0 until values.length()).map(values::getString)
        }
        val evidence = args.optJSONArray("evidence_ids") ?: JSONArray()
        require(evidence.length() <= 10)
        return ProposedAction(json.getString("action"), json.getString("observation_id"), json.optional("target_id"),
            ActionArguments(args.optional("package_name"), args.optional("text"), args.optional("direction"),
                args.optional("message"), args.optional("expected_text"), (0 until evidence.length()).map(evidence::getString), ids("cart_item_ids"), ids("cart_total_ids")),
            json.getString("reason"), json.getBoolean("requires_confirmation"))
    }
}
