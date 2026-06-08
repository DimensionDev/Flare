package dev.dimension.flare.feature.agent.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import dev.dimension.flare.feature.agent.common.AgentConversationAttachment
import dev.dimension.flare.feature.agent.common.AgentConversationEvent
import dev.dimension.flare.feature.agent.common.AgentInputRequest
import dev.dimension.flare.ui.model.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
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
    val traceHistory: ImmutableList<Trace>,
    val inputRequest: AgentInputRequest?,
    val error: Throwable?,
    private val setInput: (String) -> Unit,
    private val sendMessage: () -> Unit,
    private val selectInputRequestOption: (AgentInputRequest.Option) -> Unit,
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

    fun selectInputRequestOption(option: AgentInputRequest.Option) {
        selectInputRequestOption.invoke(option)
    }
}

@Composable
internal fun <Message : Any, Content : Any, Trace : Any, Context : Any> rememberAgentChatPresenterController(
    key: String,
    conversationId: String,
    contextFlow: Flow<Context?>,
    runAgent: (Context, String?, String) -> Flow<AgentConversationEvent<Content, Trace>>,
    userMessage: (String) -> Message,
    assistantMessage: (String, List<AgentConversationAttachment>, AgentInputRequest?) -> Message,
    isAssistantMessage: (Message) -> Boolean,
    messageInputRequest: (Message) -> AgentInputRequest? = { null },
    messageInputRequestSelected: (Message) -> Boolean = { false },
    markMessageInputRequestSelected: (Message, String, String) -> Message = { message, _, _ -> message },
    messageText: (Message) -> String,
    onInputRequestSelected: suspend (String, String) -> Unit = { _, _ -> },
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
    var traceHistory: ImmutableList<Trace> by remember(key) {
        mutableStateOf(persistentListOf())
    }
    var inputRequest: AgentInputRequest? by remember(key) {
        mutableStateOf(null)
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
    var runGeneration by remember(key) {
        mutableStateOf(0)
    }
    var initialUserInputConsumed by remember(key) {
        mutableStateOf(false)
    }
    val currentInitialMessages by rememberUpdatedState(initialMessages)

    fun updateMessages(transform: (List<Message>) -> List<Message>) {
        messages = transform(messages).toImmutableList()
    }

    fun appendTrace(trace: Trace) {
        if (traceHistory.lastOrNull() != trace) {
            traceHistory = (traceHistory + trace).toImmutableList()
        }
    }

    fun List<Message>.sameMessageTexts(other: List<Message>): Boolean =
        size == other.size &&
            zip(other).all { (left, right) ->
                messageText(left) == messageText(right)
            }

    fun List<Message>.latestOpenInputRequest(): AgentInputRequest? =
        lastOrNull { message ->
            messageInputRequest(message) != null && !messageInputRequestSelected(message)
        }?.let(messageInputRequest)

    fun List<Message>.latestOpenInputRequestForOption(option: AgentInputRequest.Option): AgentInputRequest? =
        lastOrNull { message ->
            val request = messageInputRequest(message)
            request != null &&
                !messageInputRequestSelected(message) &&
                request.options.any { requestOption ->
                    requestOption.id == option.id && requestOption.value == option.value
                }
        }?.let(messageInputRequest)

    fun runCurrentAgent(userInput: String?) {
        val contextValue =
            context
                ?: run {
                    error = missingContextError()
                    return
                }
        runJob?.cancel()
        val generation = runGeneration + 1
        runGeneration = generation
        runJob =
            scope.launch {
                isRunning = true
                currentTrace = null
                traceHistory = persistentListOf()
                error = null
                try {
                    runAgent(contextValue, userInput, conversationId).collect { event ->
                        when (event) {
                            is AgentConversationEvent.ContentLoaded -> {
                                content = event.content
                            }

                            is AgentConversationEvent.Trace -> {
                                currentTrace = event.trace
                                appendTrace(event.trace)
                            }

                            is AgentConversationEvent.Result -> {
                                currentTrace = null
                                inputRequest = event.inputRequest
                                updateMessages {
                                    it + assistantMessage(event.text, event.attachments, event.inputRequest)
                                }
                            }
                        }
                    }
                } catch (throwable: Throwable) {
                    currentTrace = null
                    if (throwable !is CancellationException && runGeneration == generation) {
                        error = throwable
                    }
                } finally {
                    if (runGeneration == generation) {
                        isRunning = false
                        runJob = null
                    }
                }
            }
    }

    LaunchedEffect(key, initialMessages) {
        if (!isRunning && initialMessages.isNotEmpty()) {
            when {
                messages.isEmpty() -> {
                    messages = initialMessages.toImmutableList()
                    inputRequest = initialMessages.latestOpenInputRequest()
                }

                messages.sameMessageTexts(initialMessages) && messages != initialMessages -> {
                    messages = initialMessages.toImmutableList()
                    inputRequest = initialMessages.latestOpenInputRequest()
                }
            }
        }
    }

    LaunchedEffect(key, conversationId) {
        contextFlow.collectLatest { contextValue ->
            runJob?.cancel()
            runJob = null
            runGeneration += 1
            messages = currentInitialMessages.toImmutableList()
            input = ""
            isRunning = false
            content = null
            currentTrace = null
            traceHistory = persistentListOf()
            inputRequest = null
            error = null
            context = contextValue
            inputRequest = messages.latestOpenInputRequest()
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
        traceHistory = traceHistory,
        inputRequest = inputRequest,
        error = error,
        setInput = {
            input = it
        },
        sendMessage = {
            val text = input.trim()
            if (text.isNotEmpty() && !isRunning) {
                input = ""
                inputRequest = null
                updateMessages {
                    it + userMessage(text)
                }
                runCurrentAgent(userInput = text)
            }
        },
        selectInputRequestOption = { option ->
            val text = option.value.trim()
            if (!option.submit && !isRunning) {
                input = text
            } else if (text.isNotEmpty() && !isRunning) {
                messages.latestOpenInputRequestForOption(option)?.let { request ->
                    updateMessages { currentMessages ->
                        currentMessages.map { message ->
                            markMessageInputRequestSelected(message, request.requestId, option.id)
                        }
                    }
                    scope.launch {
                        onInputRequestSelected(request.requestId, option.id)
                    }
                }
                input = ""
                inputRequest = null
                updateMessages {
                    it + userMessage(option.label)
                }
                runCurrentAgent(userInput = text)
            }
        },
        isAssistantMessage = isAssistantMessage,
        messageText = messageText,
    )
}
