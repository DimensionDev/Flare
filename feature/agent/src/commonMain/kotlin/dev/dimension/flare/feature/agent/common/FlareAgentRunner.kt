package dev.dimension.flare.feature.agent.common

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.ReceivedToolResults
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.ConfigureAction
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import dev.dimension.flare.common.MimeTypes
import dev.dimension.flare.data.network.ai.AiImagePreparer
import dev.dimension.flare.feature.agent.presenter.AgentMessagePart
import dev.dimension.flare.feature.agent.presenter.distinctAgentMessageParts
import dev.dimension.flare.feature.agent.runtime.AgentAvailability
import dev.dimension.flare.feature.agent.runtime.AgentImageFallback
import dev.dimension.flare.feature.agent.runtime.FlareAgentRuntime
import dev.dimension.flare.feature.agent.runtime.FlareAgentRuntimeProvider
import dev.dimension.flare.feature.agent.runtime.supportsAgentImageFallback
import dev.dimension.flare.shared.image.ImageCompressor
import dev.dimension.flare.ui.model.UiMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

@Single
internal class FlareAgentRunner(
    private val runtimeProvider: FlareAgentRuntimeProvider,
    private val toolProvider: AgentToolProvider,
    private val chatHistoryProvider: AgentChatHistoryProvider,
    private val imageCompressor: ImageCompressor,
) {
    suspend fun clearConversation(conversationId: String) {
        chatHistoryProvider.clear(conversationId)
    }

    suspend fun run(
        request: FlareAgentRequest,
        conversationId: String,
        onTrace: suspend (AgentTrace) -> Unit,
    ): AgentRunResult {
        val runtime =
            runtimeProvider.createRuntime()
                ?: throw FlareAgentUnavailableException(runtimeProvider.availability())
        val toolSet = toolProvider.resolve(request.toolContext)
        if (request.retryMode != null) {
            checkNotNull(chatHistoryProvider.consumeRetry(conversationId)) { "This attempt is no longer retryable" }.let { failure ->
                toolSet.messagePartStore.addPosts(failure.supportingParts.filterIsInstance<AgentMessagePart.PostCard>().map { it.post })
                toolSet.messagePartStore.addUsers(failure.supportingParts.filterIsInstance<AgentMessagePart.UserCard>().map { it.user })
                failure.inputRequest?.let { toolSet.inputRequestStore.set(it) }
            }
        }
        val resolvedRequest = request.withToolSet(toolSet)
        val agent =
            runtime.createAgent(
                resolvedRequest,
                onTrace,
                chatHistoryProvider,
                prepareImage =
                    if (supportsAgentImageFallback) {
                        { media, maxBytes ->
                            val image = AiImagePreparer(imageCompressor).prepare(media, maxBytes)
                            val format = requireNotNull(MimeTypes.extensionFor(image.mimeType))
                            AttachmentSource.Image(
                                AttachmentContent.Binary.Bytes(image.bytes),
                                format,
                                image.mimeType,
                                "post-image.$format",
                            )
                        }
                    } else {
                        null
                    },
                storeCheckpoint = chatHistoryProvider::storeCheckpoint,
                storeMessages = { id, messages ->
                    chatHistoryProvider.storeAssistantResponse(
                        conversationId = id,
                        messages = messages,
                        supportingParts = (request.supportingParts + toolSet.messagePartStore.snapshot()).distinctAgentMessageParts(),
                        inputRequest = toolSet.inputRequestStore.snapshot(),
                    )
                },
            )
        return try {
            AgentRunResult(
                text = agent.run(request.prompt, conversationId),
                parts = toolSet.messagePartStore.snapshot(),
                inputRequest = toolSet.inputRequestStore.snapshot(),
            )
        } catch (exception: Throwable) {
            generateSequence(exception) { it.cause }.filterIsInstance<AgentRetryableException>().firstOrNull()?.let { failure ->
                failure.supportingParts = toolSet.messagePartStore.snapshot()
                failure.inputRequest = toolSet.inputRequestStore.snapshot()
            }
            throw exception
        } finally {
            withContext(NonCancellable) {
                try {
                    agent.close()
                } finally {
                    runtime.promptExecutor.close()
                }
            }
        }
    }
}

internal fun FlareAgentRuntime.createAgent(
    request: FlareAgentRequest,
    onTrace: suspend (AgentTrace) -> Unit,
    chatHistoryProvider: ChatHistoryProvider,
    prepareImage: (suspend (UiMedia.Image, Int) -> AttachmentSource.Image)? = null,
    storeMessages: suspend (String, List<Message>) -> Unit = chatHistoryProvider::store,
    storeCheckpoint: suspend (String, List<Message>, AgentContextCheckpoint) -> Unit = { _, _, _ -> },
): AIAgent<Prompt, String> {
    val compaction = AgentContextCompaction()
    return AIAgent
        .builder()
        .promptExecutor(
            AgentImageFallback(promptExecutor, request.images, prepareImage, onTrace),
        ).llmModel(model)
        .toolRegistry(request.toolRegistry)
        .id(request.agentId)
        .prompt(
            Prompt.build(request.agentId, params = params) {
                system(request.systemPromptWithToolGuidance)
            },
        ).temperature(request.temperature)
        .maxIterations(request.maxIterations)
        .graphStrategy(
            strategy<Prompt, String>(request.strategyName) {
                val nodeAnalyze by node<Prompt, Message.Assistant>(request.analyzeNodeName) { inputPrompt ->
                    llm.writeSession {
                        prompt =
                            prompt.withMessages { messages ->
                                config.prompt.messages.filterIsInstance<Message.System>() + messages.filterNot { it is Message.System }
                            }
                    }
                    requestAgentResponse(
                        compaction,
                        chatHistoryProvider,
                        storeCheckpoint,
                        finishAfterToolResults = request.retryMode == AgentRetryMode.Finish,
                    ) {
                        if (request.retryMode == null || request.retryMode == AgentRetryMode.Restart) {
                            messages(inputPrompt.messages)
                        }
                    }
                }
                val nodeExecuteTools by nodeExecuteTools(request.executeToolsNodeName)
                val nodeSendToolResults by node<ReceivedToolResults, Message.Assistant>(request.sendToolResultsNodeName) { results ->
                    requestAgentResponse(
                        compaction,
                        chatHistoryProvider,
                        storeCheckpoint,
                        finishAfterToolResults = request.finishAfterToolResults,
                    ) {
                        user {
                            results.toolResults.forEach { toolResult -> toolResult(toolResult.toMessagePart()) }
                        }
                    }
                }

                edge(nodeStart forwardTo nodeAnalyze)
                edge(nodeAnalyze forwardTo nodeExecuteTools onToolCalls { true })
                edge(nodeAnalyze forwardTo nodeFinish onTextMessage { true })
                edge(nodeExecuteTools forwardTo nodeSendToolResults)
                if (!request.finishAfterToolResults) {
                    edge(nodeSendToolResults forwardTo nodeExecuteTools onToolCalls { true })
                }
                edge(nodeSendToolResults forwardTo nodeFinish onTextMessage { true })
            },
        ).install(
            ChatMemory,
            ConfigureAction { config ->
                config.chatHistoryProvider = chatHistoryProvider
            },
        ).install(
            EventHandler,
            ConfigureAction { config ->
                config.onAgentStarting {
                    onTrace(AgentTrace(AgentPhase.AgentStarted))
                }
                config.onStrategyStarting {
                    onTrace(AgentTrace(AgentPhase.StrategyStarted))
                }
                config.onStrategyCompleted {
                    onTrace(AgentTrace(AgentPhase.StrategyCompleted))
                }
                config.onSubgraphExecutionStarting {
                    onTrace(AgentTrace(AgentPhase.SubgraphStarted))
                }
                config.onSubgraphExecutionCompleted {
                    onTrace(AgentTrace(AgentPhase.SubgraphCompleted))
                }
                config.onSubgraphExecutionFailed {
                    onTrace(AgentTrace(AgentPhase.SubgraphFailed))
                }
                config.onLLMCallStarting {
                    onTrace(AgentTrace(AgentPhase.AskingModel, detail = it.model.id))
                }
                config.onLLMCallCompleted {
                    onTrace(AgentTrace(AgentPhase.ModelResponseReceived))
                }
                config.onLLMStreamingStarting {
                    onTrace(AgentTrace(AgentPhase.StreamingStarted, detail = it.model.id))
                }
                config.onLLMStreamingFrameReceived {
                    onTrace(AgentTrace(AgentPhase.StreamingResponse))
                }
                config.onLLMStreamingCompleted {
                    onTrace(AgentTrace(AgentPhase.StreamingCompleted))
                }
                config.onLLMStreamingFailed {
                    onTrace(AgentTrace(AgentPhase.StreamingFailed))
                }
                config.onNodeExecutionStarting {
                    onTrace(AgentTrace(AgentPhase.RunningStep))
                }
                config.onNodeExecutionCompleted {
                    // LLM callbacks run before the reply is appended and while the write session is locked.
                    if (it.output is Message.Assistant) {
                        val messages =
                            it.context.llm.writeSession {
                                prompt =
                                    prompt.withMessages { messages ->
                                        messages.map { message ->
                                            if (message.id == null) message.withAgentMessageId(Uuid.random().toString()) else message
                                        }
                                    }
                                prompt.messages
                            }
                        storeMessages(it.context.runId, messages)
                    }
                    onTrace(AgentTrace(AgentPhase.StepCompleted))
                }
                config.onNodeExecutionFailed {
                    onTrace(AgentTrace(AgentPhase.StepFailed))
                }
                config.onToolCallStarting {
                    onTrace(request.toToolTrace(it.toolName, AgentPhase.ToolCallStarted))
                }
                config.onToolCallCompleted {
                    onTrace(request.toToolTrace(it.toolName, AgentPhase.ToolCallCompleted))
                }
                config.onToolValidationFailed {
                    onTrace(request.toToolTrace(it.toolName, AgentPhase.ToolValidationFailed))
                }
                config.onToolCallFailed {
                    onTrace(request.toToolTrace(it.toolName, AgentPhase.ToolCallFailed))
                }
                config.onAgentCompleted {
                    onTrace(AgentTrace(AgentPhase.AgentCompleted))
                }
                config.onAgentExecutionFailed {
                    onTrace(AgentTrace(AgentPhase.AgentFailed))
                }
                config.onAgentClosing {
                    onTrace(AgentTrace(AgentPhase.AgentClosing))
                }
            },
        ).build()
}

private suspend fun AIAgentGraphContextBase.requestAgentResponse(
    compaction: AgentContextCompaction,
    chatHistoryProvider: ChatHistoryProvider,
    storeCheckpoint: suspend (String, List<Message>, AgentContextCheckpoint) -> Unit,
    finishAfterToolResults: Boolean = false,
    input: PromptBuilder.() -> Unit,
): Message.Assistant {
    // Another tool round needs three more nodes: tools, model, and finish.
    val summarize = !finishAfterToolResults && stateManager.withStateLock { it.iterations > config.maxAgentIterations - 3 }
    return llm.writeSession {
        appendPrompt(input)
        prompt =
            prompt.withMessages { messages ->
                messages.map { if (it.id == null) it.withAgentMessageId(Uuid.random().toString()) else it }
            }
        // Retry only from a persisted model boundary, after the whole tool batch has completed.
        chatHistoryProvider.store(runId, prompt.messages)
        try {
            compaction.request(this, { messages, checkpoint -> storeCheckpoint(runId, messages, checkpoint) }) {
                when {
                    summarize -> {
                        val originalPrompt = prompt
                        val response =
                            try {
                                appendPrompt {
                                    system(
                                        """
                                        This run is reaching its step limit. Tools are disabled for this response.
                                        Summarize completed work, remaining work, and next steps in the user's language.
                                        Explain that the user can ask you to continue. Do not claim unfinished work is complete.
                                        """.trimIndent(),
                                    )
                                }
                                requestLLMWithoutTools()
                            } finally {
                                // The limit instruction must not constrain the next user turn.
                                prompt = originalPrompt
                            }
                        check(
                            response.parts.none { it is MessagePart.Tool.Call },
                        ) { "Tools are disabled while summarizing at the step limit" }
                        appendPrompt { message(response) }
                        response
                    }

                    finishAfterToolResults -> {
                        requestLLMWithoutTools()
                    }

                    else -> {
                        requestLLM()
                    }
                }
            }
        } catch (exception: Throwable) {
            if (exception is CancellationException) {
                currentCoroutineContext().ensureActive()
                if (exception !is TimeoutCancellationException) throw exception
            }
            throw AgentRetryableException(
                exception,
                if (summarize ||
                    finishAfterToolResults
                ) {
                    AgentRetryMode.Finish
                } else {
                    AgentRetryMode.Resume
                },
            )
        }
    }
}

internal data class AgentRunResult(
    val text: String,
    val parts: List<AgentMessagePart>,
    val inputRequest: AgentPendingInputRequest?,
)

internal data class FlareAgentRequest(
    val prompt: Prompt,
    val systemPrompt: String,
    val agentId: String,
    val strategyName: String,
    val analyzeNodeName: String,
    val executeToolsNodeName: String,
    val sendToolResultsNodeName: String,
    val toolContext: AgentToolContext = AgentToolContext.Empty,
    val temperature: Double = 0.2,
    val maxIterations: Int = 256,
    val finishAfterToolResults: Boolean = false,
    val images: List<UiMedia.Image> = emptyList(),
    val supportingParts: List<AgentMessagePart> = emptyList(),
    val retryMode: AgentRetryMode? = null,
    private val toolSet: AgentToolSet = AgentToolSet.Empty,
) {
    val toolRegistry: ToolRegistry = toolSet.toolRegistry
    val systemPromptWithToolGuidance: String =
        systemPrompt
            .withAgentMarkdownGuidance()
            .withToolGuidance(toolSet.systemPromptGuidance)

    fun withToolSet(toolSet: AgentToolSet): FlareAgentRequest = copy(toolSet = toolSet)

    fun toToolTrace(
        toolName: String,
        phase: AgentPhase,
    ): AgentTrace =
        AgentTrace(
            phase = phase,
            detail = toolName,
            toolKey = toolSet.traceRegistry.keyFor(toolName, phase),
        )
}

public open class FlareAgentUnavailableException(
    public val availability: AgentAvailability,
) : IllegalStateException("Flare agent is unavailable: $availability")

private fun String.withToolGuidance(guidance: String): String =
    if (guidance.isBlank()) {
        this
    } else {
        trimEnd() + "\n\n" + guidance
    }

private fun String.withAgentMarkdownGuidance(): String =
    trimEnd() +
        "\n\n" +
        """
        Response formatting:
        - Return Markdown supported by compose-richtext.
        - Prefer paragraphs, headings, bullet lists, numbered lists, block quotes, inline code, and links when they improve readability.
        - Do not use markdown tables because they are not needed in the chat bubble layout; use compact lists instead.
        - Do not wrap the entire answer in a fenced code block unless the user explicitly asks for code.
        - If you include a Flare attachment marker such as [[post:...]] or [[user:...]], keep it exactly unchanged and place it on its own line.
        - When a tool creates an input request, write the visible prompt/confirmation copy yourself from the structured tool result and append exactly one hidden metadata comment:
          <!-- flare-agent-actions {"requestId":"...","options":[{"id":"...","label":"...","buttonType":"Primary"}]} -->
        - The buttonType enum values are Primary, Secondary, Destructive, and Cancel. Include every option id from the structured input request. Labels must be written by you in the user's language.
        - If a tool result contains inputRequestId/inputRequestOptions, do not render the options as a visible numbered or bulleted list. Write only a concise prompt; Flare renders the selectable actions from the input request.
        - Return only the final user-visible answer plus the required hidden metadata comment when applicable.
        """.trimIndent()
