package dev.dimension.flare.feature.agent.runtime

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.datastore.model.AppSettings
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

internal class AiConfigKoogBridge {
    fun availability(aiConfig: AppSettings.AiConfig): AgentAvailability =
        when (val type = aiConfig.effectiveAgentType) {
            AppSettings.AiConfig.Type.OnDevice -> {
                AgentAvailability.Unavailable(AgentAvailability.Reason.OnDeviceAiUnsupported)
            }

            is AppSettings.AiConfig.Type.OpenAI -> {
                when {
                    type.serverUrl.isBlank() -> {
                        AgentAvailability.Unavailable(AgentAvailability.Reason.MissingOpenAIEndpoint)
                    }

                    type.apiKey.isBlank() -> {
                        AgentAvailability.Unavailable(AgentAvailability.Reason.MissingOpenAIApiKey)
                    }

                    type.model.isBlank() -> {
                        AgentAvailability.Unavailable(AgentAvailability.Reason.MissingOpenAIModel)
                    }

                    else -> {
                        AgentAvailability.Available
                    }
                }
            }
        }

    fun createRuntime(
        aiConfig: AppSettings.AiConfig,
        httpClientFactory: KoogHttpClient.Factory,
    ): FlareAgentRuntime? {
        if (availability(aiConfig) != AgentAvailability.Available) {
            return null
        }

        val openAIConfig = aiConfig.effectiveAgentType as? AppSettings.AiConfig.Type.OpenAI ?: return null
        val params =
            LLMParams(
                additionalProperties =
                    buildJsonObject {
                        openAIConfig.extraBody
                            .takeIf { it.isNotBlank() }
                            ?.let { JSON.parseToJsonElement(it).jsonObject }
                            ?.filterKeys { it !in AGENT_REQUEST_FIELDS }
                            ?.forEach { (key, value) -> put(key, value) }
                        openAIConfig.reasoningEffort
                            .takeIf { it.isNotBlank() }
                            ?.let { put("reasoning_effort", it) }
                    },
            )
        val client =
            OpenAILLMClient(
                apiKey = openAIConfig.apiKey,
                settings =
                    OpenAIClientSettings(
                        baseUrl = openAIConfig.serverUrl.trimEnd('/'),
                        chatCompletionsPath = "chat/completions",
                        responsesAPIPath = "responses",
                        embeddingsPath = "embeddings",
                        moderationsPath = "moderations",
                        modelsPath = "models",
                    ),
                httpClientFactory = httpClientFactory,
            )
        val model =
            LLModel(
                provider = LLMProvider.OpenAI,
                id = openAIConfig.model,
                capabilities =
                    listOf(
                        LLMCapability.Completion,
                        LLMCapability.OpenAIEndpoint.Completions,
                        LLMCapability.Temperature,
                        LLMCapability.Tools,
                        LLMCapability.ToolChoice,
                        LLMCapability.Vision.Image,
                    ),
                contextLength = DEFAULT_CONTEXT_LENGTH,
            )

        return FlareAgentRuntime(
            promptExecutor = MultiLLMPromptExecutor(client),
            model = model,
            params = params,
        )
    }

    private companion object {
        const val DEFAULT_CONTEXT_LENGTH = 128_000L

        // The agent owns conversation history, tool selection and response transport.
        val AGENT_REQUEST_FIELDS = setOf("model", "messages", "tools", "tool_choice", "stream", "stream_options", "n")
    }
}
