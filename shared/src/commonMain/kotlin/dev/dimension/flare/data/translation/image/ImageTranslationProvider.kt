package dev.dimension.flare.data.translation.image

import dev.dimension.flare.data.datastore.model.ImageTranslationConfig
import dev.dimension.flare.data.datastore.model.ImageTranslationProvider
import dev.dimension.flare.data.network.ai.AiImageFailure
import dev.dimension.flare.data.network.ai.AiImageFallback
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

internal interface ImageTranslationAdapter {
    val provider: ImageTranslationProvider

    suspend fun translate(
        input: ImageTranslationInput,
        config: ImageTranslationConfig,
        target: String,
    ): ImageTranslationOutput
}

internal class ImageTranslationInput(
    val publicUrl: String?,
    val media: dev.dimension.flare.common.UploadMedia,
    private val prepareImage: suspend (dev.dimension.flare.common.UploadMedia) -> dev.dimension.flare.common.UploadMedia,
) {
    private var prepared: ImageTranslationInput? = null

    val mimeType: String get() = media.mimeType
    val size: Long get() = media.size

    suspend fun prepared(): ImageTranslationInput =
        prepared ?: (
            withTimeoutOrNull(60_000) {
                val preparedMedia =
                    try {
                        prepareImage(media)
                    } catch (error: Exception) {
                        when (error) {
                            is CancellationException -> {
                                throw error
                            }

                            is IllegalArgumentException, is IllegalStateException -> {
                                throw ImageTranslationException(ImageTranslationError.UnsupportedInput).also { it.addSuppressed(error) }
                            }

                            else -> {
                                throw error
                            }
                        }
                    }
                ImageTranslationInput(null, preparedMedia, prepareImage)
            }
                ?: throw ImageTranslationException(ImageTranslationError.Timeout)
        ).also { prepared = it }

    suspend fun <T> withImageFallback(request: suspend (String?) -> T): T =
        AiImageFallback(
            allowTextFallback = false,
            initialMode = if (publicUrl == null) AiImageFallback.Mode.Inline else AiImageFallback.Mode.Url,
        ).execute(
            imageFailure = { (it as? ImageTranslationException)?.imageFailure },
        ) { mode -> request(if (mode == AiImageFallback.Mode.Url) publicUrl else null) }

    suspend fun readBytes(maxBytes: Long): ByteString {
        if (size > maxBytes) throw ImageTranslationException(ImageTranslationError.UnsupportedInput)
        val buffer = okio.Buffer()
        media.forEachChunk { bytes, count -> buffer.write(bytes, 0, count) }
        return buffer.readByteString()
    }

    suspend fun writeBase64(channel: io.ktor.utils.io.ByteWriteChannel) {
        media.forEachChunk(chunkSize = 24 * 1024) { bytes, count ->
            channel.writeFully(bytes.toByteString(0, count).base64().encodeToByteArray())
        }
    }
}

internal sealed interface ImageTranslationOutput {
    data class Encoded(
        val data: String,
    ) : ImageTranslationOutput

    data class Remote(
        val url: String,
    ) : ImageTranslationOutput
}

public enum class ImageTranslationError {
    NotConfigured,
    UnsupportedInput,
    Authentication,
    RateLimited,
    Rejected,
    NoImage,
    InvalidImage,
    Network,
    Timeout,
    ;

    public val message: String
        get() =
            when (this) {
                NotConfigured -> "Configure image translation in Translation settings first."
                UnsupportedInput -> "The provider does not support this image or input format."
                Authentication -> "Check the provider credentials and service access."
                RateLimited -> "The provider rate or quota limit was reached."
                Rejected -> "The provider rejected this image translation request."
                NoImage -> "The provider did not return a complete translated image."
                InvalidImage -> "The translated image could not be decoded."
                Network -> "Image translation failed. Check the connection and provider settings."
                Timeout -> "Image translation timed out. The provider may still be processing it."
            }
}

internal class ImageTranslationException(
    val reason: ImageTranslationError,
    val imageFailure: AiImageFailure? = null,
    val retryWithFile: Boolean = false,
) : Exception(reason.name)

internal fun ImageTranslationConfig.translationPrompt(target: String): String = value("prompt").replace("{targetLanguage}", target)

internal fun ImageTranslationConfig.fingerprint(): String =
    kotlinx.serialization.json
        .buildJsonObject {
            put("protocol", kotlinx.serialization.json.JsonPrimitive("image-translation-v1"))
            put("provider", kotlinx.serialization.json.JsonPrimitive(provider.name))
            put(
                "values",
                kotlinx.serialization.json.buildJsonObject {
                    dev.dimension.flare.data.datastore.model.imageTranslationFields(provider).forEach {
                        put(it.id, kotlinx.serialization.json.JsonPrimitive(value(it.id)))
                    }
                },
            )
        }.toString()
        .encodeUtf8()
        .sha256()
        .hex()

internal fun imageTranslationLanguageCode(language: String): String {
    val parts = language.replace('_', '-').lowercase().split('-')
    return if (parts.first() == "zh") {
        when {
            "hk" in parts -> "zh-HK"
            "hant" in parts || "tw" in parts -> "zh-TW"
            else -> "zh"
        }
    } else {
        parts.first()
    }
}
