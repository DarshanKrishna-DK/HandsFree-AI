package com.example.handsfree.agent

object VoicePolicy {
    fun maySpeak(conversationEnabled: Boolean, audioDisclosureAccepted: Boolean) =
        conversationEnabled && audioDisclosureAccepted
}
