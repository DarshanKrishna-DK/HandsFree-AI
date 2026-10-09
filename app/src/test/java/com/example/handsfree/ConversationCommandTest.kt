package com.example.handsfree
import com.example.handsfree.agent.*
import org.junit.Assert.*
import org.junit.Test
class ConversationCommandTest {
    @Test fun terminalStatesAlwaysAllowAnotherTask() {
        listOf(AgentStatus.IDLE, AgentStatus.STOPPED, AgentStatus.COMPLETED, AgentStatus.FAILED).forEach { assertFalse(it.isRunning) }
        assertTrue(AgentStatus.WAITING_FOR_USER.isRunning)
        assertTrue(AgentStatus.PLANNING.isRunning)
    }
    @Test fun spokenNavigationDoesNotBecomeAFoodAnswer() {
        assertEquals(ConversationCommand.NEW_TASK, ConversationCommand.parse("Start over."))
        assertEquals(ConversationCommand.HOME, ConversationCommand.parse("Go to home screen"))
        assertEquals(ConversationCommand.PHONE_HOME, ConversationCommand.parse("Phone home"))
        assertEquals(ConversationCommand.STOP, ConversationCommand.parse("Stop conversation"))
        assertEquals(ConversationCommand.NONE, ConversationCommand.parse("I want home style food"))
    }
    @Test fun audioFromEarlierQuestionCannotApproveANewAction() {
        assertFalse(ConversationCommand.acceptsReply(3, 4))
        assertTrue(ConversationCommand.acceptsReply(4, 4))
    }
}
