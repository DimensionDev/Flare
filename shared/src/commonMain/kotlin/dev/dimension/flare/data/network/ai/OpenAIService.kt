package dev.dimension.flare.data.network.ai

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import dev.dimension.flare.common.BuildConfig
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.network.FlareLogger
import dev.dimension.flare.data.network.httpClientEngine
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import kotlin.time.Duration.Companion.minutes

internal class OpenAIService {
    suspend fun models(
        serverUrl: String,
        apiKey: String,
    ): List<String> =
        createClient(
            serverUrl = serverUrl,
            apiKey = apiKey,
        ).models()
            .map { it.id.id }
            .sorted()

    suspend fun chatCompletion(
        config: AppSettings.AiConfig.Type.OpenAI,
        prompt: String,
    ): String =
        createClient(
            serverUrl = config.serverUrl,
            apiKey = config.apiKey,
        ).chatCompletion(
            request =
                ChatCompletionRequest(
                    model = ModelId(config.model),
                    messages =
                        listOf(
                            ChatMessage(
                                role = ChatRole.User,
                                content = prompt,
                            ),
                        ),
                ),
        ).choices
            .firstOrNull()
            ?.message
            ?.content
            .orEmpty()
            .trim()

    suspend fun chatCompletionOrNull(
        config: AppSettings.AiConfig.Type.OpenAI,
        prompt: String,
    ): String? =
        if (config.serverUrl.isBlank() || config.apiKey.isBlank() || config.model.isBlank()) {
            null
        } else {
            chatCompletion(
                config = config,
                prompt = prompt,
            )
        }

    private fun createClient(
        serverUrl: String,
        apiKey: String,
    ): OpenAI =
        OpenAI(
            OpenAIConfig(
                host = OpenAIHost(baseUrl = serverUrl),
                token = apiKey,
                engine = httpClientEngine,
                timeout =
                    Timeout(
                        request = 1.minutes,
                        socket = 1.minutes,
                        connect = 1.minutes,
                    ),
                httpClientConfig = {
                    install(Logging) {
                        logger = FlareLogger
                        level =
                            if (BuildConfig.debug) {
                                LogLevel.ALL
                            } else {
                                LogLevel.BODY
                            }
                    }
                },
            ),
        )
}
