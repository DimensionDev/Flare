package dev.dimension.flare.data.translation.image

import dev.dimension.flare.data.datastore.model.ImageTranslationConfig
import dev.dimension.flare.data.datastore.model.ImageTranslationProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

internal class GeminiImageTranslationAdapter(
    private val client: HttpClient,
) : ImageTranslationAdapter {
    override val provider = ImageTranslationProvider.Gemini

    override suspend fun translate(
        input: ImageTranslationInput,
        config: ImageTranslationConfig,
        target: String,
    ): ImageTranslationOutput {
        val endpoint = checkedImageServiceUrl(config.value("baseUrl")) + "/interactions"

        suspend fun request(url: String?): kotlinx.serialization.json.JsonObject {
            if (url == null && input.size > 1024 * 1024) {
                val metadata =
                    buildJsonObject {
                        put("model", config.value("model"))
                        put("store", false)
                        put("response_format", buildJsonObject { put("type", "image") })
                    }.toString().dropLast(1)
                val text =
                    buildJsonObject {
                        put("type", "text")
                        put("text", config.translationPrompt(target))
                    }
                val prefix = metadata + ",\"input\":[" + text + ",{\"type\":\"image\",\"mime_type\":\"" + input.mimeType + "\",\"data\":\""
                return client.imagePost(endpoint, streamingImageBody(input, prefix, "\"}]}", 20L * 1024 * 1024)) {
                    header("x-goog-api-key", config.value("apiKey"))
                }
            }
            return client.imagePost(
                endpoint,
                buildJsonObject {
                    put("model", config.value("model"))
                    put("store", false)
                    put("response_format", buildJsonObject { put("type", "image") })
                    put(
                        "input",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", config.translationPrompt(target))
                                },
                            )
                            add(
                                buildJsonObject {
                                    put("type", "image")
                                    put("mime_type", input.mimeType)
                                    if (url != null) {
                                        put("uri", url)
                                    } else {
                                        put("data", input.readBytes(20L * 1024 * 1024).base64())
                                    }
                                },
                            )
                        },
                    )
                },
            ) { header("x-goog-api-key", config.value("apiKey")) }
        }
        val result =
            try {
                request(input.publicUrl)
            } catch (error: ImageTranslationException) {
                if (input.publicUrl == null || !error.retryWithBytes) throw error
                request(null)
            }
        val image =
            result["steps"]
                ?.jsonArray
                .orEmpty()
                .map { it.jsonObject }
                .filter { it.text("type") == "model_output" }
                .flatMap { it["content"]?.jsonArray.orEmpty() }
                .map { it.jsonObject }
                .lastOrNull { it.text("type") == "image" } ?: throw ImageTranslationException(ImageTranslationError.NoImage)
        return image.text("data")?.let(ImageTranslationOutput::Encoded)
            ?: image.text("uri")?.let(ImageTranslationOutput::Remote)
            ?: throw ImageTranslationException(ImageTranslationError.NoImage)
    }
}
