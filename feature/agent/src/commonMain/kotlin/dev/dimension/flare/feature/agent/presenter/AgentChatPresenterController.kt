package dev.dimension.flare.feature.agent.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.feature.agent.common.AgentChatHistoryMessage
import dev.dimension.flare.feature.agent.common.AgentChatRoom
import dev.dimension.flare.feature.agent.common.AgentConversationEvent
import dev.dimension.flare.feature.agent.common.AgentInputRequest
import dev.dimension.flare.feature.agent.common.AgentTrace
import dev.dimension.flare.ui.model.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
internal data class AgentChatPresenterController<Content : Any, Context : Any>(
    val room: AgentChatRoom,
    val messages: ImmutableList<AgentChatHistoryMessage>,
    val input: String,
    val content: Content?,
    private val setInput: (String) -> Unit,
    private val sendMessage: () -> Unit,
    private val selectInputRequestOption: (AgentInputRequest.Option) -> Unit,
) {
    val insight: UiState<String> =
        when {
            room.errorMessage != null -> {
                UiState.Error(IllegalStateException(room.errorMessage))
            }

            room.isRunning && messages.none { it.isAssistant } -> {
                UiState.Loading()
            }

            else -> {
                messages
                    .lastOrNull { it.isAssistant }
                    ?.parts
                    ?.agentMessageText()
                    ?.let { UiState.Success(it) }
                    ?: UiState.Loading()
            }
        }

    val canSend: Boolean = input.isNotBlank() && !room.isRunning

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
internal fun <Content : Any, Context : Any> rememberAgentChatPresenterController(
    key: String,
    conversationId: String,
    room: AgentChatRoom,
    messageRecords: List<AgentChatHistoryMessage>,
    contextFlow: Flow<Context?>,
    runAgent: (Context, String?, String) -> Flow<AgentConversationEvent<Content, AgentTrace>>,
    onUserMessageSubmitted: suspend (String) -> Unit = {},
    onInputRequestOptionSubmitted: suspend (AgentInputRequest.Option) -> Unit = { option ->
        val displayText = option.label.trim()
        if (displayText.isNotEmpty()) {
            onUserMessageSubmitted(displayText)
        }
    },
    onInputRequestSelected: suspend (String, String) -> Unit = { _, _ -> },
    onAgentRunCompleted: suspend () -> Unit = {},
    onRoomStateChanged: suspend (errorMessage: String?) -> Unit,
    missingContextError: () -> Throwable,
    autoRunOnContext: Boolean = true,
    initialUserInput: String? = null,
): AgentChatPresenterController<Content, Context> {
    val scope = rememberCoroutineScope()
    val runtime =
        remember(key, conversationId) {
            AgentChatPresenterRuntime<Context>()
        }
    val messages =
        remember(messageRecords) {
            messageRecords.toImmutableList()
        }
    var input by remember(key, conversationId) {
        mutableStateOf("")
    }
    var content by remember(key, conversationId) {
        mutableStateOf<Content?>(null)
    }
    var isRunning by remember(key, conversationId) {
        mutableStateOf(false)
    }
    var currentTrace by remember(key, conversationId) {
        mutableStateOf<AgentTrace?>(null)
    }

    DisposableEffect(key, conversationId) {
        onDispose {
            runtime.runJob?.cancel()
            runtime.runJob = null
            runtime.titleGenerationJob?.cancel()
            runtime.titleGenerationJob = null
        }
    }

    suspend fun setPersistentError(errorMessage: String?) {
        onRoomStateChanged(errorMessage)
    }

    fun setRuntimeState(
        running: Boolean,
        trace: AgentTrace?,
    ) {
        isRunning = running
        currentTrace = trace
    }

    fun appendTrace(trace: AgentTrace) {
        isRunning = true
        if (currentTrace != trace) {
            currentTrace = trace
        }
    }

    fun scheduleAgentRunCompleted() {
        if (runtime.titleGenerationJob?.isActive == true) {
            return
        }
        runtime.titleGenerationJob =
            scope.launch {
                try {
                    onAgentRunCompleted()
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) {
                        throw throwable
                    }
                }
            }
    }

    fun List<AgentChatHistoryMessage>.latestOpenInputRequest(): AgentInputRequest? =
        asReversed()
            .firstNotNullOfOrNull { message ->
                message.parts.latestOpenAgentInputRequest()
            }

    fun List<AgentChatHistoryMessage>.latestOpenInputRequestForOption(option: AgentInputRequest.Option): AgentInputRequest? =
        asReversed()
            .firstNotNullOfOrNull { message ->
                message.parts.latestOpenAgentInputRequestForOption(option)
            }

    fun runCurrentAgent(userInput: String?) {
        val contextValue =
            runtime.context
                ?: run {
                    val throwable = missingContextError()
                    scope.launch {
                        setRuntimeState(running = false, trace = null)
                        setPersistentError(throwable.message)
                    }
                    return
                }
        runtime.runJob?.cancel()
        val generation = runtime.runGeneration + 1
        runtime.runGeneration = generation
        runtime.runJob =
            scope.launch {
                setRuntimeState(running = true, trace = null)
                setPersistentError(null)
                var failed = false
                var cancelled = false
                try {
                    runAgent(contextValue, userInput, conversationId).collect { event ->
                        when (event) {
                            is AgentConversationEvent.ContentLoaded -> {
                                content = event.content
                            }

                            is AgentConversationEvent.Trace -> {
                                appendTrace(event.trace)
                            }

                            is AgentConversationEvent.Result -> {
                                setRuntimeState(running = true, trace = null)
                            }
                        }
                    }
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) {
                        cancelled = true
                    } else if (runtime.runGeneration == generation) {
                        failed = true
                        setRuntimeState(running = false, trace = null)
                        setPersistentError(throwable.message)
                    }
                } finally {
                    if (runtime.runGeneration == generation) {
                        runtime.runJob = null
                        if (!failed) {
                            withContext(NonCancellable) {
                                setRuntimeState(running = false, trace = null)
                                setPersistentError(null)
                            }
                            if (!cancelled) {
                                scheduleAgentRunCompleted()
                            }
                        }
                    }
                }
            }
    }

    LaunchedEffect(key, conversationId) {
        contextFlow.collectLatest { contextValue ->
            val previousContext = runtime.context
            if (runtime.contextInitialized && previousContext == contextValue) {
                return@collectLatest
            }
            val wasInitialized = runtime.contextInitialized
            runtime.contextInitialized = true
            runtime.context = contextValue
            if (!wasInitialized) {
                input = ""
                content = null
                setRuntimeState(running = false, trace = null)
            }
            if (contextValue != null && (!wasInitialized || previousContext == null)) {
                setPersistentError(null)
            }
            val initialText = initialUserInput?.trim()?.takeIf { it.isNotEmpty() }
            if (!runtime.initialUserInputConsumed && initialText != null && contextValue != null) {
                runtime.initialUserInputConsumed = true
                onUserMessageSubmitted(initialText)
                runCurrentAgent(userInput = initialText)
            } else if (autoRunOnContext && !runtime.autoRunOnContextConsumed && contextValue != null) {
                runtime.autoRunOnContextConsumed = true
                runCurrentAgent(userInput = null)
            }
        }
    }

    val runtimeRoom =
        room.copy(
            isRunning = isRunning,
            currentTrace = currentTrace,
        )

    return AgentChatPresenterController(
        room = runtimeRoom,
        messages = messages,
        input = input,
        content = content,
        setInput = {
            input = it
        },
        sendMessage = {
            val text = input.trim()
            if (text.isNotEmpty() && !isRunning) {
                if (runtime.context == null) {
                    val throwable = missingContextError()
                    scope.launch {
                        setRuntimeState(running = false, trace = null)
                        setPersistentError(throwable.message)
                    }
                } else {
                    input = ""
                    scope.launch {
                        onUserMessageSubmitted(text)
                        runCurrentAgent(userInput = text)
                    }
                }
            }
        },
        selectInputRequestOption = { option ->
            val text = option.value.trim()
            if (!option.submit && !isRunning) {
                input = text
            } else if (text.isNotEmpty() && !isRunning) {
                if (runtime.context == null) {
                    val throwable = missingContextError()
                    scope.launch {
                        setRuntimeState(running = false, trace = null)
                        setPersistentError(throwable.message)
                    }
                } else {
                    val request = messages.latestOpenInputRequestForOption(option)
                    input = ""
                    scope.launch {
                        request?.let { selectedRequest ->
                            onInputRequestSelected(selectedRequest.requestId, option.id)
                        }
                        onInputRequestOptionSubmitted(option)
                        runCurrentAgent(userInput = text)
                    }
                }
            }
        },
    )
}

private class AgentChatPresenterRuntime<Context : Any> {
    var context: Context? = null
    var contextInitialized: Boolean = false
    var runJob: Job? = null
    var titleGenerationJob: Job? = null
    var runGeneration: Int = 0
    var initialUserInputConsumed: Boolean = false
    var autoRunOnContextConsumed: Boolean = false
}
