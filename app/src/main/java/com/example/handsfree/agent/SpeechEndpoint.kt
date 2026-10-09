package com.example.handsfree.agent

/** Monotonic time, independent of recorder scheduling and network latency. */
class SpeechEndpoint(private val startedAt: Long) {
    var hasSpeech = false; private set
    private var lastSpeechAt = startedAt
    private var precedingSpeechAt: Long? = null
    fun secondsRemaining(now: Long): Int = ((5_000 - (now - lastSpeechAt)).coerceAtLeast(0) + 999).toInt() / 1000
    fun sample(now: Long, amplitude: Int): Boolean {
        if (amplitude > 900) {
            // Two consecutive samples avoid submitting an isolated tap or click as speech.
            if (precedingSpeechAt?.let { now - it <= 350 } == true) {
                hasSpeech = true; lastSpeechAt = now
            }
            precedingSpeechAt = now
        } else precedingSpeechAt = null
        return now - startedAt >= 45_000 ||
            if (hasSpeech) now - lastSpeechAt >= 5_000 else now - startedAt >= 8_000
    }
}
