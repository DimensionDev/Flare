package dev.dimension.flare.feature.agent.presenter

import androidx.compose.runtime.Composable
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
import kotlinx.collections.immutable.persistentListOf
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
    onRoomStateChanged: suspend (
        isRunning: Boolean,
        currentTrace: AgentTrace?,
        traceHistory: List<AgentTrace>,
        errorMessage: String?,
    ) -> Unit,
    missingContextError: () -> Throwable,
    autoRunOnContext: Boolean = true,
    initialUserInput: String? = null,
): AgentChatPresenterController<Content, Context> {
    val scope = rememberCoroutineScope()
    val messages = messageRecords.toImmutableList()
    var input by remember(key) {
        mutableStateOf("")
    }
    var content by remember(key) {
        mutableStateOf<Content?>(null)
    }
    var context by remember(key) {
        mutableStateOf<Context?>(null)
    }
    var contextInitialized by remember(key) {
        mutableStateOf(false)
    }
    var runJob by remember(key) {
        mutableStateOf<Job?>(null)
    }
    var titleGenerationJob by remember(key) {
        mutableStateOf<Job?>(null)
    }
    var runGeneration by remember(key) {
        mutableStateOf(0)
    }
    var initialUserInputConsumed by remember(key) {
        mutableStateOf(false)
    }
    var traceHistoryDraft: ImmutableList<AgentTrace> by remember(key) {
        mutableStateOf(persistentListOf())
    }

    suspend fun setRoomState(
        isRunning: Boolean,
        currentTrace: AgentTrace?,
        traceHistory: List<AgentTrace>,
        errorMessage: String?,
    ) {
        onRoomStateChanged(
            isRunning,
            currentTrace,
            traceHistory,
            errorMessage,
        )
    }

    suspend fun appendTrace(trace: AgentTrace) {
        if (traceHistoryDraft.lastOrNull() != trace) {
            traceHistoryDraft = (traceHistoryDraft + trace).toImmutableList()
        }
        setRoomState(
            isRunning = true,
            currentTrace = trace,
            traceHistory = traceHistoryDraft,
            errorMessage = null,
        )
    }

    fun scheduleAgentRunCompleted() {
        if (titleGenerationJob?.isActive == true) {
            return
        }
        titleGenerationJob =
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
            context
                ?: run {
                    val throwable = missingContextError()
                    scope.launch {
                        setRoomState(
                            isRunning = false,
                            currentTrace = null,
                            traceHistory = traceHistoryDraft,
                            errorMessage = throwable.message,
                        )
                    }
                    return
                }
        runJob?.cancel()
        val generation = runGeneration + 1
        runGeneration = generation
        runJob =
            scope.launch {
                traceHistoryDraft = persistentListOf()
                setRoomState(
                    isRunning = true,
                    currentTrace = null,
                    traceHistory = traceHistoryDraft,
                    errorMessage = null,
                )
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
                                setRoomState(
                                    isRunning = true,
                                    currentTrace = null,
                                    traceHistory = traceHistoryDraft,
                                    errorMessage = null,
                                )
                            }
                        }
                    }
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) {
                        cancelled = true
                    } else if (runGeneration == generation) {
                        failed = true
                        setRoomState(
                            isRunning = false,
                            currentTrace = null,
                            traceHistory = traceHistoryDraft,
                            errorMessage = throwable.message,
                        )
                    }
                } finally {
                    if (runGeneration == generation) {
                        runJob = null
                        if (!failed) {
                            withContext(NonCancellable) {
                                setRoomState(
                                    isRunning = false,
                                    currentTrace = null,
                                    traceHistory = traceHistoryDraft,
                                    errorMessage = null,
                                )
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
            if (contextInitialized && context == contextValue) {
                return@collectLatest
            }
            contextInitialized = true
            runJob?.cancel()
            runJob = null
            runGeneration += 1
            input = ""
            content = null
            traceHistoryDraft = persistentListOf()
            context = contextValue
            setRoomState(
                isRunning = false,
                currentTrace = null,
                traceHistory = traceHistoryDraft,
                errorMessage = null,
            )
            val initialText = initialUserInput?.trim()?.takeIf { it.isNotEmpty() }
            if (!initialUserInputConsumed && initialText != null && contextValue != null) {
                initialUserInputConsumed = true
                onUserMessageSubmitted(initialText)
                runCurrentAgent(userInput = initialText)
            } else if (autoRunOnContext && contextValue != null) {
                runCurrentAgent(userInput = null)
            }
        }
    }

    return AgentChatPresenterController(
        room = room,
        messages = messages,
        input = input,
        content = content,
        setInput = {
            input = it
        },
        sendMessage = {
            val text = input.trim()
            if (text.isNotEmpty() && !room.isRunning) {
                if (context == null) {
                    val throwable = missingContextError()
                    scope.launch {
                        setRoomState(
                            isRunning = false,
                            currentTrace = null,
                            traceHistory = traceHistoryDraft,
                            errorMessage = throwable.message,
                        )
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
            if (!option.submit && !room.isRunning) {
                input = text
            } else if (text.isNotEmpty() && !room.isRunning) {
                if (context == null) {
                    val throwable = missingContextError()
                    scope.launch {
                        setRoomState(
                            isRunning = false,
                            currentTrace = null,
                            traceHistory = traceHistoryDraft,
                            errorMessage = throwable.message,
                        )
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
