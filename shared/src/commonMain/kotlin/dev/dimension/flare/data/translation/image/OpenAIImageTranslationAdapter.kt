package dev.dimension.flare.data.translation.image

import dev.dimension.flare.data.datastore.model.ImageTranslationConfig
import dev.dimension.flare.data.datastore.model.ImageTranslationProvider
import dev.dimension.flare.data.network.appendMedia
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

internal class OpenAIImageTranslationAdapter(
    private val client: HttpClient,
) : ImageTranslationAdapter {
    override val provider = ImageTranslationProvider.OpenAIImages

    override suspend fun translate(
        input: ImageTranslationInput,
        config: ImageTranslationConfig,
        target: String,
    ): ImageTranslationOutput {
        val endpoint = checkedImageServiceUrl(config.value("baseUrl")) + "/images/edits"
        val format = config.value("inputFormat")

        suspend fun multipartRequest() =
            coroutineScope {
                val input = input.prepared()
                if (input.size >= 50L * 1024 * 1024) throw ImageTranslationException(ImageTranslationError.UnsupportedInput)
                val uploadScope = this
                client.imagePost(
                    endpoint,
                    MultiPartFormDataContent(
                        formData {
                            append("model", config.value("model"))
                            append("prompt", config.translationPrompt(target))
                            append("n", "1")
                            appendMedia("image[]", input.media, uploadScope)
                        },
                    ),
                ) { bearerAuth(config.value("apiKey")) }
            }

        suspend fun request(image: String) =
            client.imagePost(
                endpoint,
                buildJsonObject {
                    put("model", config.value("model"))
                    put("prompt", config.translationPrompt(target))
                    put("n", 1)
                    put("images", buildJsonArray { add(buildJsonObject { put("image_url", image) }) })
                },
            ) { bearerAuth(config.value("apiKey")) }

        suspend fun inlineRequest(): ImageTranslationResponse {
            val input = input.prepared()
            // JSON image_url is limited to 20 MiB of characters; multipart accepts larger original files.
            val maxInlineBytes = (20L * 1024 * 1024 - 64) / 4 * 3
            if (input.size > maxInlineBytes) {
                if (format == "Automatic") return multipartRequest()
                throw ImageTranslationException(ImageTranslationError.UnsupportedInput)
            }
            if (input.size > 1024 * 1024) {
                val metadata =
                    buildJsonObject {
                        put("model", config.value("model"))
                        put("prompt", config.translationPrompt(target))
                        put("n", 1)
                    }.toString().dropLast(1)
                val prefix = metadata + ",\"images\":[{\"image_url\":\"data:" + input.mimeType + ";base64,"
                return client.imagePost(endpoint, streamingImageBody(input, prefix, "\"}]}", maxInlineBytes)) {
                    bearerAuth(config.value("apiKey"))
                }
            }
            return request("data:${input.mimeType};base64,${input.readBytes(maxInlineBytes).base64()}")
        }
        val result =
            if (format == "Multipart") {
                multipartRequest()
            } else {
                try {
                    input.withImageFallback { url ->
                        if (url == null) inlineRequest() else request(url)
                    }
                } catch (error: ImageTranslationException) {
                    if (format != "Automatic" || !error.retryWithFile) throw error
                    multipartRequest()
                }
            }
        val image =
            result.body["data"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject ?: throw result.failure()
        return image.text("b64_json")?.let(ImageTranslationOutput::Encoded)
            ?: image.text("url")?.let(ImageTranslationOutput::Remote)
            ?: throw result.failure()
    }
}
