package dev.dimension.flare.feature.agent.common

import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResults
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.ConfigureAction
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import dev.dimension.flare.feature.agent.runtime.AgentAvailability
import dev.dimension.flare.feature.agent.runtime.FlareAgentRuntime
import dev.dimension.flare.feature.agent.runtime.FlareAgentRuntimeProvider
import org.koin.core.annotation.Single

@Single
internal class FlareAgentRunner(
    private val runtimeProvider: FlareAgentRuntimeProvider,
    private val toolProvider: AgentToolProvider,
    private val chatHistoryProvider: AgentChatHistoryProvider,
) {
    suspend fun clearConversation(conversationId: String) {
        chatHistoryProvider.clear(conversationId)
    }

    suspend fun run(
        request: FlareAgentRequest,
        conversationId: String,
        onTrace: suspend (AgentTrace) -> Unit,
    ): String {
        val runtime =
            runtimeProvider.createRuntime()
                ?: throw FlareAgentUnavailableException(runtimeProvider.availability())
        val toolSet = toolProvider.resolve(request.toolContext)
        val resolvedRequest = request.withToolSet(toolSet)
        val agent = runtime.createAgent(resolvedRequest, onTrace)
        return try {
            agent.run(request.prompt, conversationId)
        } finally {
            agent.close()
        }
    }

    private fun FlareAgentRuntime.createAgent(
        request: FlareAgentRequest,
        onTrace: suspend (AgentTrace) -> Unit,
    ): AIAgent<Prompt, String> =
        AIAgent
            .builder()
            .promptExecutor(promptExecutor)
            .llmModel(model)
            .toolRegistry(request.toolRegistry)
            .id(request.agentId)
            .systemPrompt(request.systemPromptWithToolGuidance)
            .temperature(request.temperature)
            .maxIterations(request.maxIterations)
            .graphStrategy(
                strategy<Prompt, String>(request.strategyName) {
                    val nodeAnalyze by node<Prompt, Message.Assistant>(request.analyzeNodeName) { prompt ->
                        llm.writeSession {
                            appendPrompt {
                                messages(prompt.messages)
                            }
                            requestLLM()
                        }
                    }
                    val nodeExecuteTools by nodeExecuteTools(request.executeToolsNodeName)
                    val nodeSendToolResults by nodeLLMSendToolResults(request.sendToolResultsNodeName)

                    edge(nodeStart forwardTo nodeAnalyze)
                    edge(nodeAnalyze forwardTo nodeExecuteTools onToolCalls { true })
                    edge(nodeAnalyze forwardTo nodeFinish onTextMessage { true })
                    edge(nodeExecuteTools forwardTo nodeSendToolResults)
                    edge(nodeSendToolResults forwardTo nodeExecuteTools onToolCalls { true })
                    edge(nodeSendToolResults forwardTo nodeFinish onTextMessage { true })
                },
            ).install(
                ChatMemory,
                ConfigureAction { config ->
                    config.chatHistoryProvider = chatHistoryProvider
                    config.windowSize(request.chatMemoryWindowSize)
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
    val maxIterations: Int = 16,
    val chatMemoryWindowSize: Int = 20,
    private val toolSet: AgentToolSet = AgentToolSet.Empty,
) {
    val toolRegistry: ToolRegistry = toolSet.toolRegistry
    val systemPromptWithToolGuidance: String = systemPrompt.withToolGuidance(toolSet.systemPromptGuidance)

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

internal class FlareAgentUnavailableException(
    val availability: AgentAvailability,
) : IllegalStateException("Flare agent is unavailable: $availability")

private fun String.withToolGuidance(guidance: String): String =
    if (guidance.isBlank()) {
        this
    } else {
        trimEnd() + "\n\n" + guidance
    }
