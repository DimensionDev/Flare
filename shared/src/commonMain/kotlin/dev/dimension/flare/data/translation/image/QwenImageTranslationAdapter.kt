package dev.dimension.flare.data.translation.image

import dev.dimension.flare.data.datastore.model.ImageTranslationConfig
import dev.dimension.flare.data.datastore.model.ImageTranslationProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

internal class QwenImageTranslationAdapter(
    private val client: HttpClient,
) : ImageTranslationAdapter {
    override val provider = ImageTranslationProvider.Qwen

    override suspend fun translate(
        input: ImageTranslationInput,
        config: ImageTranslationConfig,
        target: String,
    ): ImageTranslationOutput {
        // shortcut: this contract accepts public URLs only; add byte-upload support before enabling local recovery.
        val url = input.publicUrl ?: throw ImageTranslationException(ImageTranslationError.UnsupportedInput)
        // The synchronous 2.0 contract avoids persisting or recreating cloud tasks.
        if (config.value("model") != "qwen-mt-image-2.0") throw ImageTranslationException(ImageTranslationError.UnsupportedInput)
        val result =
            client.imagePost(
                checkedImageServiceUrl(config.value("baseUrl")) + "/services/aigc/image2image/image-synthesis",
                buildJsonObject {
                    put("model", config.value("model"))
                    put(
                        "input",
                        buildJsonObject {
                            put("image_url", url)
                            put("source_lang", "auto")
                            put("target_lang", imageTranslationLanguageCode(target))
                        },
                    )
                },
            ) { bearerAuth(config.value("apiKey")) }
        if (result.body.text("code") != null) throw result.failure(ImageTranslationError.Rejected)
        return result.body["output"]
            ?.jsonObject
            ?.text("image_url")
            ?.let(ImageTranslationOutput::Remote)
            ?: throw result.failure()
    }
}
