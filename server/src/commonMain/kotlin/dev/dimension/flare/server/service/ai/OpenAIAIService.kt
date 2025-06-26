package dev.dimension.flare.server.service.ai

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import dev.dimension.flare.server.common.Log
import dev.dimension.flare.server.common.createEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.server.config.ApplicationConfig
import kotlin.time.Duration.Companion.minutes

internal class OpenAIAIService(
    private val baseUrl: String,
    private val token: String,
    private val model: String,
) : AIService {
    constructor(config: ApplicationConfig) : this(
        baseUrl = config.property("ai.openai.baseUrl").getString(),
        token = config.property("ai.openai.token").getString(),
        model = config.property("ai.openai.model").getString()
    )

    private val openai by lazy {
        val host = OpenAIHost(
            baseUrl = baseUrl,
        )
        val config = OpenAIConfig(
            host = host,
            token = token,
            engine = createEngine(),
            httpClientConfig = {
                install(Logging) {
                    logger =
                        object : Logger {
                            override fun log(message: String) {
                                Log.trace("OpenAI", message)
                            }
                        }
                    level = LogLevel.ALL
                }
                install(HttpTimeout) {
                    connectTimeoutMillis =
                        2.minutes.inWholeMilliseconds
                    requestTimeoutMillis =
                        2.minutes.inWholeMilliseconds
                    socketTimeoutMillis = 2.minutes.inWholeMilliseconds
                }
            }
        )
        OpenAI(config)
    }

    override suspend fun generate(prompt: String): String {
        val response = openai.chatCompletion(
            request = ChatCompletionRequest(
                model = ModelId(model),
                messages = listOf(
                    ChatMessage(
                        role = ChatRole.User,
                        content = prompt,
                    ),
                ),
            )
        )
        return response.choices.firstOrNull()?.message?.content ?:
            throw IllegalStateException("No response from OpenAI")
    }
}