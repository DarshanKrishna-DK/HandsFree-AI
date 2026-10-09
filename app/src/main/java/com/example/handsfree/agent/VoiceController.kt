package com.example.handsfree.agent

import com.example.handsfree.ui.uiText
import com.example.handsfree.R
import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import com.example.handsfree.ui.AppLanguage
import android.util.Base64
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

/** Conversation lifetime is explicit and independent of the current agent task. */
class VoiceController private constructor(private val context: Context) {
    companion object {
        private var instance: VoiceController? = null
        fun get(context: Context) = instance ?: VoiceController(context.applicationContext).also { instance = it }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val backend = BackendClient()
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var player: MediaPlayer? = null
    private var playbackFile: File? = null
    private var playbackJob: Job? = null
    private var conversationJob: Job? = null
    private var captureGeneration = 0L
    private var sessionGeneration = 0L
    private fun disclosed() = context.getSharedPreferences("conversation", 0).getBoolean("audio_disclosure", false)
    var sessionReady by mutableStateOf(false)
    var sessionActive by mutableStateOf(false); private set
    var conversationEnabled by mutableStateOf(true)
    var speaking by mutableStateOf(false); private set
    var lastTranscript by mutableStateOf(""); private set
    var onListeningChanged: ((Boolean) -> Unit)? = null
    var recording by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf(""); private set
    val language get() = AppLanguage.code(context)
    var spokenResponses by mutableStateOf(true)

    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Called only from the visible activity after disclosure and microphone permission. */
    fun beginSession(): Boolean {
        if (sessionActive) return true
        if (!conversationEnabled || !disclosed() || !hasPermission()) return false
        return try {
            ContextCompat.startForegroundService(context, Intent(context, VoiceSessionService::class.java))
            sessionActive = true
            val session = ++sessionGeneration
            conversationJob = scope.launch {
                try {
                    withTimeout(5_000) { while (!sessionReady) delay(100) }
                    var failures = 0
                    while (isActive && sessionActive && session == sessionGeneration) {
                        if (context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                            message = context.uiText(R.string.locked)
                            break
                        }
                        if (speaking) { delay(100); continue }
                        if (com.example.handsfree.accessibility.ServiceConnection.service?.privateInputVisible() == true) {
                            AgentController.get(context).privateInputDetected()
                            break
                        }
                        val controller = AgentController.get(context)
                        val revision = controller.voiceRevision
                        val capturedLanguage = language
                        val file = try { captureSpeech() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            if (++failures >= 3) { message = context.uiText(R.string.mic_unavailable); break }
                            delay(1000); continue
                        }
                        if (file == null) { delay(150); continue }
                        try {
                            busy = true
                            message = context.uiText(R.string.transcribing)
                            val encoded = withContext(Dispatchers.IO) { Base64.encodeToString(file.readBytes(), Base64.NO_WRAP) }
                            val body = JSONObject().put("audio_base64", encoded).put("language_code", capturedLanguage)
                            val sentAt = SystemClock.elapsedRealtime()
                            val result = withTimeout(30_000) {
                                JSONObject(backend.request("/voice/transcribe", body.toString().toRequestBody("application/json".toMediaType())))
                            }
                            ensureActive()
                            if (session == sessionGeneration && capturedLanguage == language) {
                                if (com.example.handsfree.BuildConfig.DEBUG) android.util.Log.d("HandsFreeVoice", "transcription_ms=${SystemClock.elapsedRealtime() - sentAt}")
                                lastTranscript = result.getString("transcript").trim()
                                busy = false
                                failures = 0
                                message = context.uiText(R.string.heard, lastTranscript)
                                controller.receiveVoice(lastTranscript, revision)
                            }
                        } catch (_: TimeoutCancellationException) {
                            message = context.uiText(R.string.voice_timeout)
                            if (++failures >= 3) break
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            message = context.uiText(R.string.not_understood)
                            if (++failures >= 3) { message = context.uiText(R.string.voice_unavailable); break }
                            delay(1500)
                        } finally { file.delete(); if (session == sessionGeneration) busy = false }
                        delay(150)
                    }
                } catch (_: TimeoutCancellationException) { message = context.uiText(R.string.voice_start_failed) }
                finally { if (session == sessionGeneration) closeSession(clearMessage = false) }
            }
            true
        } catch (_: Exception) { message = context.uiText(R.string.voice_type_fallback); false }
    }

    /** Silence is discarded locally. Segments automatically restart until the user ends the session. */
    private suspend fun captureSpeech(): File? {
        val epoch = captureGeneration
        val file = File.createTempFile("conversation-", ".m4a", context.cacheDir)
        recordingFile = file
        val capture = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        recorder = capture
        var delivered = false
        try {
            capture.setAudioSource(MediaRecorder.AudioSource.MIC)
            capture.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            capture.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            capture.setAudioSamplingRate(16000)
            capture.setAudioEncodingBitRate(64000)
            capture.setOutputFile(file.absolutePath)
            capture.prepare(); capture.start()
            recording = true; message = context.uiText(R.string.listening); onListeningChanged?.invoke(true)
            val endpoint = SpeechEndpoint(SystemClock.elapsedRealtime())
            while (epoch == captureGeneration && sessionActive && !speaking) {
                delay(100)
                if (epoch != captureGeneration) return null
                if (context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return null
                val now = SystemClock.elapsedRealtime()
                if (endpoint.sample(now, capture.maxAmplitude)) break
                message = if (endpoint.hasSpeech && endpoint.secondsRemaining(now) < 5)
                    context.uiText(R.string.pause_countdown, endpoint.secondsRemaining(now)) else context.uiText(R.string.listening)
            }
            if (epoch != captureGeneration || !sessionActive || speaking) return null
            capture.stop()
            delivered = endpoint.hasSpeech
            return if (endpoint.hasSpeech) file else null
        } finally {
            if (recorder === capture) {
                runCatching { capture.release() }; recorder = null; recordingFile = null
                recording = false; onListeningChanged?.invoke(false)
            }
            if (!delivered) file.delete()
        }
    }

    fun endSession() = closeSession(clearMessage = true)
    private fun closeSession(clearMessage: Boolean) {
        sessionGeneration++
        sessionActive = false
        conversationJob?.cancel(); conversationJob = null
        onListeningChanged = null
        cancelTurn()
        busy = false; sessionReady = false
        context.stopService(Intent(context, VoiceSessionService::class.java))
        if (clearMessage) { message = ""; lastTranscript = "" }
    }
    fun serviceStopped() { if (sessionActive) closeSession(clearMessage = false) }
    fun cancelTurn() { cancelRecording(); stopPlayback() }
    fun cancelRecording() {
        captureGeneration++
        recorder?.let { runCatching { it.stop() }; runCatching { it.release() } }
        recorder = null; recordingFile?.delete(); recordingFile = null
        recording = false; onListeningChanged?.invoke(false)
    }
    fun ask(text: String) { cancelTurn(); speak(text) }

    fun speak(text: String) {
        if (!sessionActive || !VoicePolicy.maySpeak(conversationEnabled, disclosed()) || !spokenResponses || text.isBlank()) return
        cancelRecording(); stopPlayback()
        speaking = true
        playbackJob = scope.launch {
            try {
                val payload = JSONObject().put("text", text.take(1000)).put("language_code", language)
                val response = withTimeout(30_000) {
                    JSONObject(backend.request("/voice/synthesize", payload.toString().toRequestBody("application/json".toMediaType())))
                }
                ensureActive()
                val file = File.createTempFile("response-", ".wav", context.cacheDir)
                playbackFile = file
                withContext(Dispatchers.IO) { file.writeBytes(Base64.decode(response.getString("audio_base64"), Base64.DEFAULT)) }
                ensureActive()
                player = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnCompletionListener { stopPlayback() }
                    setOnErrorListener { _, _, _ -> stopPlayback(); message = context.uiText(R.string.audio_unavailable); true }
                    prepare(); start()
                }
            } catch (_: TimeoutCancellationException) {
                stopPlayback(); message = context.uiText(R.string.audio_unavailable)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { stopPlayback(); message = context.uiText(R.string.audio_unavailable) }
        }
    }
    fun stopPlayback() {
        speaking = false
        playbackJob?.cancel(); playbackJob = null
        player?.release(); player = null; playbackFile?.delete(); playbackFile = null
    }
}
