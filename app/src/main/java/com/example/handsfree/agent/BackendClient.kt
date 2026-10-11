package com.example.handsfree.agent

import com.example.handsfree.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BackendClient : ActionPlanner {
    private val http = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(130, TimeUnit.SECONDS).callTimeout(135, TimeUnit.SECONDS).build()
    private val base = "http://127.0.0.1:8000"

    suspend fun health(): JSONObject = JSONObject(request("/health"))

    override suspend fun nextAction(request: PlannerRequest): ProposedAction {
        val payload = PlannerProtocol.encode(request).toString().toRequestBody("application/json".toMediaType())
        return ActionProposalCodec.decode(request("/agent/next-action", payload), request.screen.id)
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

}
