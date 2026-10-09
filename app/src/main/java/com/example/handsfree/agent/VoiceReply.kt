package com.example.handsfree.agent

enum class VoiceReply { STOP, APPROVE, REJECT, ANSWER, UNCLEAR;
    companion object {
        fun parse(text: String, clarification: Boolean): VoiceReply {
            val reply = text.trim().trimEnd('.', '!', '?').lowercase()
            if (reply in setOf("stop", "stop task", "cancel task", "ನಿಲ್ಲಿಸಿ")) return STOP
            if (clarification) return if (reply.isBlank()) UNCLEAR else ANSWER
            return when (reply) {
                "approve", "yes approve", "i approve", "ಅನುಮೋದಿಸಿ" -> APPROVE
                "decline", "no", "reject", "ಬೇಡ" -> REJECT
                else -> UNCLEAR
            }
        }
    }
}
