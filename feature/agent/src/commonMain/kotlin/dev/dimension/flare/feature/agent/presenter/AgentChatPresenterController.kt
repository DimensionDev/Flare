package dev.dimension.flare.feature.agent.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.collectPagingState
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.feature.agent.common.AgentChatHistoryMessage
import dev.dimension.flare.feature.agent.common.AgentChatHistoryProvider
import dev.dimension.flare.feature.agent.common.AgentChatRoom
import dev.dimension.flare.feature.agent.common.AgentConversationEvent
import dev.dimension.flare.feature.agent.common.AgentInputRequest
import dev.dimension.flare.feature.agent.common.AgentTrace
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
internal data class AgentChatPresenterController<Content : Any, Context : Any>(
    val room: AgentChatRoom,
    val messages: PagingState<AgentChatHistoryMessage>,
    val input: String,
    val content: Content?,
    private val setInput: (String) -> Unit,
    private val sendMessage: () -> Unit,
    private val selectInputRequestOption: (AgentInputRequest.Option) -> Unit,
) {
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
    onInitialContentLoaded: suspend (Content) -> Unit = {},
    onAgentRunCompleted: suspend () -> Unit = {},
    onRoomRuntimeStateChanged: suspend (isRunning: Boolean) -> Unit = {},
    onRoomStateChanged: suspend (errorMessage: String?) -> Unit,
    missingContextError: () -> Throwable,
    autoRunOnContext: Boolean = true,
    initialUserInput: String? = null,
): AgentChatPresenterController<Content, Context> {
    val runtime =
        remember(conversationId) {
            AgentChatRunRegistry.retainRuntime(conversationId)
        }
    DisposableEffect(conversationId, runtime) {
        onDispose {
            AgentChatRunRegistry.releaseRuntime(conversationId, runtime)
        }
    }
    val runState by runtime.state.collectAsState()
    val historyProvider: AgentChatHistoryProvider by koinInject()
    val messages by remember(conversationId) {
        historyProvider.observeMessages(conversationId)
    }.collectPagingState()
    var input by remember(key, conversationId) {
        mutableStateOf("")
    }

    suspend fun setPersistentError(errorMessage: String?) {
        onRoomStateChanged(errorMessage)
    }

    suspend fun setRuntimeState(
        running: Boolean,
        trace: AgentTrace?,
    ) {
        val previousState = runtime.state.value
        AgentChatRunRegistry.updateState(runtime) { state ->
            state.copy(
                isRunning = running,
                currentTrace = trace,
            )
        }
        if (previousState.isRunning != running) {
            onRoomRuntimeStateChanged(running)
        }
    }

    suspend fun appendTrace(trace: AgentTrace) {
        if (runtime.state.value.currentTrace != trace || !runtime.state.value.isRunning) {
            AgentChatRunRegistry.updateState(runtime) { state ->
                state.copy(
                    isRunning = true,
                    currentTrace = trace,
                )
            }
        }
    }

    fun scheduleAgentRunCompleted() {
        if (runtime.titleGenerationJob?.isActive == true) {
            return
        }
        AgentChatRunRegistry.launchTitleGeneration(conversationId, runtime) {
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
        @Suppress("UNCHECKED_CAST")
        val contextValue =
            runtime.context as? Context
                ?: run {
                    val throwable = missingContextError()
                    AgentChatRunRegistry.launchRuntimeTask(conversationId, runtime) {
                        setRuntimeState(running = false, trace = null)
                        setPersistentError(throwable.message)
                    }
                    return
                }
        runtime.runJob?.cancel()
        val generation = runtime.runGeneration + 1
        runtime.runGeneration = generation
        AgentChatRunRegistry.launchRun(conversationId, runtime) {
            setRuntimeState(running = true, trace = null)
            setPersistentError(null)
            var failed = false
            var cancelled = false
            try {
                runAgent(contextValue, userInput, conversationId).collect { event ->
                    when (event) {
                        is AgentConversationEvent.ContentLoaded -> {
                            if (userInput == null && !runtime.initialContentMessageStored) {
                                runtime.initialContentMessageStored = true
                                onInitialContentLoaded(event.content)
                            }
                            AgentChatRunRegistry.updateState(runtime) { state ->
                                state.copy(
                                    content = event.content,
                                )
                            }
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
            }
            if (contextValue != null && (!wasInitialized || previousContext == null)) {
                setPersistentError(null)
            }
            val initialText = initialUserInput?.trim()?.takeIf { it.isNotEmpty() }
            if (!runtime.initialUserInputConsumed && initialText != null && contextValue != null) {
                runtime.initialUserInputConsumed = true
                AgentChatRunRegistry.launchRuntimeTask(conversationId, runtime) {
                    onUserMessageSubmitted(initialText)
                    runCurrentAgent(userInput = initialText)
                }
            } else if (autoRunOnContext && !runtime.autoRunOnContextConsumed && contextValue != null) {
                runtime.autoRunOnContextConsumed = true
                runCurrentAgent(userInput = null)
            }
        }
    }

    val runtimeRoom =
        room.copy(
            isRunning = runState.isRunning,
            currentTrace = runState.currentTrace,
        )

    @Suppress("UNCHECKED_CAST")
    val content = runState.content as? Content

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
            if (text.isNotEmpty() && !runtime.state.value.isRunning) {
                if (runtime.context == null) {
                    val throwable = missingContextError()
                    AgentChatRunRegistry.launchRuntimeTask(conversationId, runtime) {
                        setRuntimeState(running = false, trace = null)
                        setPersistentError(throwable.message)
                    }
                } else {
                    input = ""
                    AgentChatRunRegistry.launchRuntimeTask(conversationId, runtime) {
                        onUserMessageSubmitted(text)
                        runCurrentAgent(userInput = text)
                    }
                }
            }
        },
        selectInputRequestOption = { option ->
            val text = option.value.trim()
            if (!option.submit && !runtime.state.value.isRunning) {
                input = text
            } else if (text.isNotEmpty() && !runtime.state.value.isRunning) {
                if (runtime.context == null) {
                    val throwable = missingContextError()
                    AgentChatRunRegistry.launchRuntimeTask(conversationId, runtime) {
                        setRuntimeState(running = false, trace = null)
                        setPersistentError(throwable.message)
                    }
                } else {
                    val currentMessages = messages.snapshotMessages()
                    val request = currentMessages.latestOpenInputRequestForOption(option)
                    input = ""
                    AgentChatRunRegistry.launchRuntimeTask(conversationId, runtime) {
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

private fun PagingState<AgentChatHistoryMessage>.snapshotMessages(): ImmutableList<AgentChatHistoryMessage> =
    when (this) {
        is PagingState.Success -> {
            (0 until itemCount)
                .mapNotNull { index -> peek(index) }
                .toImmutableList()
        }

        is PagingState.Empty,
        is PagingState.Error,
        is PagingState.Loading,
        -> {
            persistentListOf()
        }
    }

internal data class AgentChatPresenterRuntimeState(
    val isRunning: Boolean = false,
    val currentTrace: AgentTrace? = null,
    val content: Any? = null,
)

internal class AgentChatPresenterRuntime {
    val state: MutableStateFlow<AgentChatPresenterRuntimeState> = MutableStateFlow(AgentChatPresenterRuntimeState())
    var context: Any? = null
    var contextInitialized: Boolean = false
    var runJob: Job? = null
    var titleGenerationJob: Job? = null
    val runtimeTaskJobs: MutableSet<Job> = mutableSetOf()
    var activeTaskCount: Int = 0
    var retainedPresenterCount: Int = 0
    var runGeneration: Int = 0
    var initialUserInputConsumed: Boolean = false
    var initialContentMessageStored: Boolean = false
    var autoRunOnContextConsumed: Boolean = false
}

internal object AgentChatRunRegistry {
    private val scope = CoroutineScope(SupervisorJob())
    private val runtimes = MutableStateFlow<Map<String, AgentChatPresenterRuntime>>(emptyMap())

    fun retainRuntime(conversationId: String): AgentChatPresenterRuntime {
        val runtime = runtime(conversationId)
        runtime.retainedPresenterCount += 1
        return runtime
    }

    fun releaseRuntime(
        conversationId: String,
        runtime: AgentChatPresenterRuntime,
    ) {
        if (runtime.retainedPresenterCount > 0) {
            runtime.retainedPresenterCount -= 1
        }
        releaseIfIdle(conversationId, runtime)
    }

    private fun runtime(conversationId: String): AgentChatPresenterRuntime {
        runtimes.value[conversationId]?.let { return it }

        val createdRuntime = AgentChatPresenterRuntime()
        var selectedRuntime = createdRuntime
        runtimes.update { currentRuntimes ->
            currentRuntimes[conversationId]?.let { existingRuntime ->
                selectedRuntime = existingRuntime
                currentRuntimes
            } ?: currentRuntimes + (conversationId to createdRuntime)
        }
        return selectedRuntime
    }

    fun updateState(
        runtime: AgentChatPresenterRuntime,
        transform: (AgentChatPresenterRuntimeState) -> AgentChatPresenterRuntimeState,
    ) {
        val state = transform(runtime.state.value)
        runtime.state.value = state
    }

    fun launchRuntimeTask(
        conversationId: String,
        runtime: AgentChatPresenterRuntime,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        runtime.activeTaskCount += 1
        lateinit var job: Job
        job =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    block()
                } finally {
                    runtime.runtimeTaskJobs.remove(job)
                    runtime.activeTaskCount -= 1
                    releaseIfIdle(conversationId, runtime)
                }
            }
        runtime.runtimeTaskJobs.add(job)
        job.start()
        return job
    }

    fun launchRun(
        conversationId: String,
        runtime: AgentChatPresenterRuntime,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        lateinit var job: Job
        job =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    block()
                } finally {
                    if (runtime.runJob === job) {
                        runtime.runJob = null
                    }
                    releaseIfIdle(conversationId, runtime)
                }
            }
        runtime.runJob = job
        job.start()
        return job
    }

    fun launchTitleGeneration(
        conversationId: String,
        runtime: AgentChatPresenterRuntime,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        lateinit var job: Job
        job =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    block()
                } finally {
                    if (runtime.titleGenerationJob === job) {
                        runtime.titleGenerationJob = null
                    }
                    releaseIfIdle(conversationId, runtime)
                }
            }
        runtime.titleGenerationJob = job
        job.start()
        return job
    }

    fun hasRuntime(conversationId: String): Boolean = runtimes.value[conversationId] != null

    fun activeRuntimeCount(): Int = runtimes.value.size

    fun cancel(conversationId: String) {
        val runtime = runtimes.value[conversationId] ?: return
        runtime.runGeneration += 1
        runtime.state.value = AgentChatPresenterRuntimeState()
        runtime.runJob?.cancel()
        runtime.titleGenerationJob?.cancel()
        runtime.runtimeTaskJobs.toList().forEach { job ->
            job.cancel()
        }
        releaseIfIdle(conversationId, runtime)
    }

    fun resetForTesting() {
        runtimes.value.values.forEach { runtime ->
            runtime.runJob?.cancel()
            runtime.titleGenerationJob?.cancel()
            runtime.runtimeTaskJobs.forEach { job ->
                job.cancel()
            }
        }
        runtimes.value = emptyMap()
    }

    private fun releaseIfIdle(
        conversationId: String,
        runtime: AgentChatPresenterRuntime,
    ) {
        if (!runtime.isIdle) {
            return
        }
        runtimes.update { currentRuntimes ->
            if (currentRuntimes[conversationId] === runtime) {
                currentRuntimes - conversationId
            } else {
                currentRuntimes
            }
        }
    }

    private val AgentChatPresenterRuntime.isIdle: Boolean
        get() =
            retainedPresenterCount <= 0 &&
                activeTaskCount <= 0 &&
                runJob?.isActive != true &&
                titleGenerationJob?.isActive != true
}
