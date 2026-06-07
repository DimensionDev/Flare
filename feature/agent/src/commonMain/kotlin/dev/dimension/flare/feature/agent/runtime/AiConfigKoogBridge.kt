package dev.dimension.flare.feature.agent.runtime

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import dev.dimension.flare.data.datastore.model.AppSettings

internal class AiConfigKoogBridge {
    fun availability(aiConfig: AppSettings.AiConfig): AgentAvailability =
        when (val type = aiConfig.type) {
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

        val openAIConfig = aiConfig.type as? AppSettings.AiConfig.Type.OpenAI ?: return null
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
            aiConfig = aiConfig,
        )
    }

    private companion object {
        const val DEFAULT_CONTEXT_LENGTH = 128_000L
    }
}
