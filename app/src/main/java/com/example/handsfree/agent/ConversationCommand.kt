package com.example.handsfree.agent

enum class ConversationCommand { STOP, NEW_TASK, HOME, PHONE_HOME, NONE;
    companion object {
        fun parse(text: String): ConversationCommand = when (text.trim().trimEnd('.', '!', '?').lowercase()) {
            "stop", "stop conversation", "end conversation", "ನಿಲ್ಲಿಸಿ", "ನಿಲ್ಲಿಸು", "ಮಾತುಕತೆ ನಿಲ್ಲಿಸಿ", "ಸಂಭಾಷಣೆ ನಿಲ್ಲಿಸಿ" -> STOP
            "new task", "new conversation", "start over", "cancel task", "stop task", "ಹೊಸ ಕೆಲಸ", "ಹೊಸ ಮಾತುಕತೆ", "ಮತ್ತೆ ಪ್ರಾರಂಭಿಸಿ", "ಕೆಲಸ ರದ್ದುಮಾಡಿ" -> NEW_TASK
            "home", "go home", "go to home", "go to home screen", "handsfree home", "ಮುಖಪುಟ", "ಮನೆಗೆ ಹೋಗು", "ಮುಖಪುಟಕ್ಕೆ ಹೋಗಿ" -> HOME
            "phone home", "go to phone home", "open phone home", "ಫೋನ್ ಮುಖಪುಟ", "ಫೋನ್ ಮುಖಪುಟಕ್ಕೆ ಹೋಗಿ" -> PHONE_HOME
            else -> NONE
        }
        fun acceptsReply(capturedRevision: Long, currentRevision: Long) = capturedRevision == currentRevision
    }
}

val AgentStatus.isRunning: Boolean get() = this in setOf(AgentStatus.UNDERSTANDING, AgentStatus.OBSERVING,
    AgentStatus.PLANNING, AgentStatus.EXECUTING, AgentStatus.VERIFYING, AgentStatus.WAITING_FOR_USER)
