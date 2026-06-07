package dev.dimension.flare.feature.agent.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.feature.agent.common.AgentConversationEvent
import dev.dimension.flare.ui.model.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Immutable
internal data class AgentChatPresenterController<Message : Any, Content : Any, Trace : Any, Context : Any>(
    val messages: ImmutableList<Message>,
    val input: String,
    val isRunning: Boolean,
    val content: Content?,
    val currentTrace: Trace?,
    val error: Throwable?,
    private val setInput: (String) -> Unit,
    private val sendMessage: () -> Unit,
    private val isAssistantMessage: (Message) -> Boolean,
    private val messageText: (Message) -> String,
) {
    val insight: UiState<String> =
        when {
            error != null -> {
                UiState.Error(error)
            }

            isRunning && messages.none(isAssistantMessage) -> {
                UiState.Loading()
            }

            else -> {
                messages
                    .lastOrNull(isAssistantMessage)
                    ?.let { UiState.Success(messageText(it)) }
                    ?: UiState.Loading()
            }
        }

    val canSend: Boolean = input.isNotBlank() && !isRunning

    fun setInput(value: String) {
        setInput.invoke(value)
    }

    fun sendMessage() {
        sendMessage.invoke()
    }
}

@Composable
internal fun <Message : Any, Content : Any, Trace : Any, Context : Any> rememberAgentChatPresenterController(
    key: String,
    conversationId: String,
    contextFlow: Flow<Context?>,
    runAgent: (Context, String?, String) -> Flow<AgentConversationEvent<Content, Trace>>,
    userMessage: (String) -> Message,
    assistantMessage: (String) -> Message,
    isAssistantMessage: (Message) -> Boolean,
    messageText: (Message) -> String,
    missingContextError: () -> Throwable,
    autoRunOnContext: Boolean = true,
    initialUserInput: String? = null,
    initialMessages: List<Message> = emptyList(),
): AgentChatPresenterController<Message, Content, Trace, Context> {
    val scope = rememberCoroutineScope()
    var messages: ImmutableList<Message> by remember(key) {
        mutableStateOf(persistentListOf<Message>())
    }
    var input by remember(key) {
        mutableStateOf("")
    }
    var isRunning by remember(key) {
        mutableStateOf(false)
    }
    var content by remember(key) {
        mutableStateOf<Content?>(null)
    }
    var currentTrace by remember(key) {
        mutableStateOf<Trace?>(null)
    }
    var error by remember(key) {
        mutableStateOf<Throwable?>(null)
    }
    var context by remember(key) {
        mutableStateOf<Context?>(null)
    }
    var runJob by remember(key) {
        mutableStateOf<Job?>(null)
    }
    var initialUserInputConsumed by remember(key) {
        mutableStateOf(false)
    }

    fun updateMessages(transform: (List<Message>) -> List<Message>) {
        messages = transform(messages).toImmutableList()
    }

    fun runCurrentAgent(userInput: String?) {
        val contextValue =
            context
                ?: run {
                    error = missingContextError()
                    return
                }
        runJob?.cancel()
        runJob =
            scope.launch {
                isRunning = true
                currentTrace = null
                error = null
                try {
                    runAgent(contextValue, userInput, conversationId).collect { event ->
                        when (event) {
                            is AgentConversationEvent.ContentLoaded -> {
                                content = event.content
                            }

                            is AgentConversationEvent.Trace -> {
                                currentTrace = event.trace
                            }

                            is AgentConversationEvent.Result -> {
                                currentTrace = null
                                updateMessages {
                                    it + assistantMessage(event.text)
                                }
                            }
                        }
                    }
                } catch (throwable: Throwable) {
                    currentTrace = null
                    error = throwable
                } finally {
                    isRunning = false
                }
            }
    }

    LaunchedEffect(key, conversationId, initialMessages) {
        contextFlow.collectLatest { contextValue ->
            runJob?.cancel()
            messages = initialMessages.toImmutableList()
            input = ""
            isRunning = false
            content = null
            currentTrace = null
            error = null
            context = contextValue
            val initialText = initialUserInput?.trim()?.takeIf { it.isNotEmpty() }
            if (!initialUserInputConsumed && initialText != null) {
                initialUserInputConsumed = true
                updateMessages {
                    it + userMessage(initialText)
                }
                runCurrentAgent(userInput = initialText)
            } else if (autoRunOnContext) {
                runCurrentAgent(userInput = null)
            }
        }
    }

    return AgentChatPresenterController(
        messages = messages,
        input = input,
        isRunning = isRunning,
        content = content,
        currentTrace = currentTrace,
        error = error,
        setInput = {
            input = it
        },
        sendMessage = {
            val text = input.trim()
            if (text.isNotEmpty() && !isRunning) {
                input = ""
                updateMessages {
                    it + userMessage(text)
                }
                runCurrentAgent(userInput = text)
            }
        },
        isAssistantMessage = isAssistantMessage,
        messageText = messageText,
    )
}
