package dev.dimension.flare.data.network.ai

import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import dev.dimension.flare.common.BuildConfig
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.network.FlareLogger
import dev.dimension.flare.data.network.createHttpClientEngine
import dev.dimension.flare.data.network.ktorClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
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
    ): String {
        val body = buildChatCompletionBody(config = config, prompt = prompt)
        val url = "${config.serverUrl.trimEnd('/')}/chat/completions"
        return ktorClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 1.minutes.inWholeMilliseconds
                socketTimeoutMillis = 1.minutes.inWholeMilliseconds
                connectTimeoutMillis = 1.minutes.inWholeMilliseconds
            }
        }.use { client ->
            val response =
                client
                    .post(url) {
                        bearerAuth(config.apiKey)
                        setBody(TextContent(body.toString(), ContentType.Application.Json))
                    }.bodyAsText()
            JSON
                .decodeFromString(ChatCompletion.serializer(), response)
                .choices
        }.firstOrNull()
            ?.message
            ?.content
            .orEmpty()
            .trim()
    }

    internal fun buildChatCompletionBody(
        config: AppSettings.AiConfig.Type.OpenAI,
        prompt: String,
    ) = buildJsonObject {
        config.extraBody
            .takeIf { it.isNotBlank() }
            ?.let { JSON.parseToJsonElement(it).jsonObject }
            ?.forEach { (key, value) -> put(key, value) }
        put("model", config.model)
        put(
            "messages",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", prompt)
                    },
                )
            },
        )
        config.reasoningEffort
            .takeIf { it.isNotBlank() }
            ?.let { put("reasoning_effort", it) }
    }

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
                engine = createHttpClientEngine(),
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
