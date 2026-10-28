package dev.dimension.flare.data.network.ai

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public enum class AiImageFailure { RetryWithBytes, Unsupported }

/** One recovery sequence per request; reuse the instance to retain the mode within an Agent run. */
@HiddenFromObjC
public class AiImageFallback(
    private val allowTextFallback: Boolean,
    initialMode: Mode = Mode.Url,
) {
    public enum class Mode { Url, Inline, Text }

    private var mode = initialMode

    public suspend fun <T> execute(
        imageFailure: (Exception) -> AiImageFailure?,
        request: suspend (Mode) -> T,
    ): T {
        if (mode == Mode.Text) return request(mode)
        var previousError: Exception? = null
        if (mode == Mode.Url) {
            try {
                return request(mode)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                when (imageFailure(error)) {
                    AiImageFailure.Unsupported -> {
                        return text(request, error)
                    }

                    AiImageFailure.RetryWithBytes -> {
                        mode = Mode.Inline
                        previousError = error
                    }

                    null -> {
                        throw error
                    }
                }
            }
        }
        try {
            return request(Mode.Inline)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            previousError?.let(error::addSuppressed)
            return text(request, error)
        }
    }

    private suspend fun <T> text(
        request: suspend (Mode) -> T,
        previousError: Exception,
    ): T {
        if (!allowTextFallback) throw previousError
        mode = Mode.Text
        try {
            return request(mode)
        } catch (error: Exception) {
            if (error !is CancellationException) error.addSuppressed(previousError)
            throw error
        }
    }
}

@HiddenFromObjC
public fun classifyAiImageFailure(
    statusCode: Int?,
    body: String,
): AiImageFailure? {
    if (statusCode !in setOf(400, 413, 415, 422)) return null
    val error = runCatching { Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject }.getOrNull()
    val code = runCatching { error?.get("code")?.jsonPrimitive?.content }.getOrNull()
    val uriViolation =
        runCatching {
            error?.get("details")?.jsonArray.orEmpty().any { detail ->
                detail.jsonObject["fieldViolations"]?.jsonArray.orEmpty().any {
                    it.jsonObject["field"]
                        ?.jsonPrimitive
                        ?.content
                        ?.endsWith(".uri") == true
                }
            }
        }.getOrDefault(false)
    if (code in setOf("invalid_image_url", "image_url_unavailable", "image_download_failed", "failed_to_download_image") || uriViolation) {
        return AiImageFailure.RetryWithBytes
    }
    val message =
        runCatching { error?.get("message")?.jsonPrimitive?.content }
            .getOrNull()
            .orEmpty()
            .ifBlank { body }
            .lowercase()
            .replace(Regex("""https?://[^\s"<>]+"""), "")
    if (!Regex("""\b(?:image|images|image_url)\b""").containsMatchIn(message)) return null
    return when {
        listOf("not support", "unsupported", "unknown variant", "expected `text`", "expected 'text'").any { it in message } -> {
            AiImageFailure.Unsupported
        }

        listOf("download", "fetch", "too large", "too big", "exceed", "image size", "image dimensions", "image resolution").any {
            it in
                message
        } -> {
            AiImageFailure.RetryWithBytes
        }

        else -> {
            null
        }
    }
}
