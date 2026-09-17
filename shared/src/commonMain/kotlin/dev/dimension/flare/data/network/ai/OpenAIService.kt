package dev.dimension.flare.data.network.ai

import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.network.ktorClient
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.content.TextContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.minutes

@Single
internal class OpenAIService(
    private val httpClientFactory: (HttpClientConfig<*>.() -> Unit) -> HttpClient = ::ktorClient,
) {
    suspend fun models(
        serverUrl: String,
        apiKey: String,
    ): List<String> =
        createClient(retryModels = true).use { client ->
            val response =
                client
                    .get(serverUrl) {
                        url.appendPathSegments("models")
                        bearerAuth(apiKey)
                    }.bodyAsText()
            JSON
                .decodeFromString(ModelsResponse.serializer(), response)
                .data
                .map { it.id }
                .sorted()
        }

    suspend fun chatCompletion(
        config: AppSettings.AiConfig.Type.OpenAI,
        prompt: String,
    ): String {
        val body = buildChatCompletionBody(config = config, prompt = prompt)
        val url = "${config.serverUrl.trimEnd('/')}/chat/completions"
        return createClient()
            .use { client ->
                val response =
                    client
                        .post(url) {
                            bearerAuth(config.apiKey)
                            setBody(TextContent(body.toString(), ContentType.Application.Json))
                        }.bodyAsText()
                JSON
                    .decodeFromString(ChatCompletionResponse.serializer(), response)
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

    private fun createClient(retryModels: Boolean = false): HttpClient =
        httpClientFactory {
            expectSuccess = true
            install(Logging) {
                sanitizeHeader { it == HttpHeaders.Authorization }
            }
            if (retryModels) {
                install(HttpRequestRetry) {
                    retryIf(maxRetries = 3) { _, response -> response.status == HttpStatusCode.TooManyRequests }
                    // Preserve the model-list retry timing used by Aallam 4.1.0.
                    exponentialDelay(base = 2.0, baseDelayMs = 1.minutes.inWholeMilliseconds)
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 1.minutes.inWholeMilliseconds
                socketTimeoutMillis = 1.minutes.inWholeMilliseconds
                connectTimeoutMillis = 1.minutes.inWholeMilliseconds
            }
        }

    @Serializable
    private data class ModelsResponse(
        val data: List<Model>,
    )

    @Serializable
    private data class Model(
        val id: String,
    )

    @Serializable
    private data class ChatCompletionResponse(
        val choices: List<Choice>,
    )

    @Serializable
    private data class Choice(
        val message: Message,
    )

    @Serializable
    private data class Message(
        val content: String? = null,
    )
}
