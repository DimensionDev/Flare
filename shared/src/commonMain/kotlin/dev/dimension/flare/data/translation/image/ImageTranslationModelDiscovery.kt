package dev.dimension.flare.data.translation.image

import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.datastore.model.ImageTranslationConfig
import dev.dimension.flare.data.datastore.model.ImageTranslationProvider
import dev.dimension.flare.data.network.ai.OpenAIService
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.core.remaining
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.koin.core.annotation.Single

@Single
internal class ImageTranslationModelDiscovery(
    private val openAIService: OpenAIService,
    private val clientFactory: () -> HttpClient = { imageTranslationHttpClient() },
) {
    suspend fun models(config: ImageTranslationConfig): List<String> =
        withTimeout(15_000) {
            val baseUrl = checkedImageServiceUrl(config.value("baseUrl"))
            when (config.provider) {
                ImageTranslationProvider.OpenAIImages -> {
                    openAIService.models(baseUrl, config.value("apiKey"))
                }

                ImageTranslationProvider.Gemini -> {
                    clientFactory().use { client ->
                        val models = mutableListOf<String>()
                        val seenTokens = mutableSetOf<String>()
                        var token: String? = null
                        do {
                            val response =
                                client.get("$baseUrl/models") {
                                    header("x-goog-api-key", config.value("apiKey"))
                                    parameter("pageSize", 1000)
                                    token?.let { parameter("pageToken", it) }
                                }
                            check(response.status.value in 200..299) { "Model discovery failed" }
                            val channel = response.bodyAsChannel()
                            val body = channel.readBuffer(4L * 1024 * 1024 + 1)
                            if (body.remaining > 4L * 1024 * 1024) {
                                channel.cancel()
                                error("Model catalogue is too large")
                            }
                            val page = JSON.parseToJsonElement(body.readText()).jsonObject
                            models +=
                                page["models"]?.jsonArray.orEmpty().mapNotNull {
                                    it.jsonObject
                                        .text("name")
                                        ?.removePrefix("models/")
                                        ?.takeIf(String::isNotBlank)
                                }
                            token = page.text("nextPageToken")?.takeIf(String::isNotBlank)
                            check(token == null || seenTokens.add(token)) { "Repeated model page" }
                        } while (token != null)
                        models
                    }
                }

                else -> {
                    emptyList()
                }
            }.filter(String::isNotBlank).distinct().sorted()
        }
}
