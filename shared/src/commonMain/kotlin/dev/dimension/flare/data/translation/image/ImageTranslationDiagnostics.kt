package dev.dimension.flare.data.translation.image

import dev.dimension.flare.data.datastore.model.ImageTranslationConfig
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

internal data class ImageTranslationDiagnostics(
    val httpStatus: Int? = null,
    val code: String? = null,
    val message: String? = null,
    val requestId: String? = null,
)

internal class ImageTranslationStageException(
    val stage: String,
) : Exception()

internal fun JsonObject?.imageTranslationDiagnostics(
    httpStatus: Int,
    requestId: String?,
    fallbackMessage: String? = null,
): ImageTranslationDiagnostics {
    val response = (this?.get("Response") as? JsonObject) ?: this
    val error =
        (response?.get("error") as? JsonObject) ?: (response?.get("Error") as? JsonObject)
            ?: response?.takeIf { httpStatus !in 200..299 || it.text("code") != null || it.text("error") != null }
    return ImageTranslationDiagnostics(
        httpStatus = httpStatus,
        code = error?.text("code") ?: error?.text("Code") ?: error?.text("status") ?: error?.text("type"),
        message = error?.text("message") ?: error?.text("Message") ?: error?.text("detail") ?: error?.text("error") ?: fallbackMessage,
        requestId = requestId ?: response?.text("request_id") ?: response?.text("RequestId") ?: response?.text("requestId"),
    )
}

internal fun Throwable.imageTranslationDetails(
    config: ImageTranslationConfig,
    target: String,
): String {
    val failure = this as? ImageTranslationException
    val diagnostics = failure?.diagnostics
    val stage = failure?.stage ?: suppressedExceptions.filterIsInstance<ImageTranslationStageException>().firstOrNull()?.stage
    val reason =
        failure?.reason ?: if (this is TimeoutCancellationException) ImageTranslationError.Timeout else ImageTranslationError.Network
    val localCause =
        generateSequence(this) { it.cause }
            .take(8)
            .flatMap { sequenceOf(it) + it.suppressedExceptions.asSequence() }
            .firstOrNull { it !is ImageTranslationException && !it.message.isNullOrBlank() }
    val details =
        buildString {
            appendLine("Time: ${Clock.System.now()}")
            appendLine("Provider: ${config.provider.title}")
            config.value("model").takeIf { it.isNotBlank() }?.let { appendLine("Model: $it") }
            appendLine("Target language: $target")
            appendLine("Stage: ${stage ?: "Translate image"}")
            appendLine("Reason: ${reason.message}")
            diagnostics?.httpStatus?.let { appendLine("HTTP status: $it") }
            diagnostics?.code?.let { appendLine("Provider code: $it") }
            diagnostics?.requestId?.let { appendLine("Request ID: $it") }
            (diagnostics?.message ?: localCause?.message)?.let { appendLine("Details: $it") }
            if (suppressedExceptions.any { it is ImageTranslationException }) appendLine("An earlier image input attempt also failed.")
        }
    return details.withoutImageData()
}

internal fun String.withoutImageData(): String {
    val text =
        replace(
            Regex("""(?i)(["']?\b(?:b64_json|base64|image_base64|image_data)["']?\s*[:=]\s*)(?:"[^"]*"|'[^']*'|[A-Za-z0-9+/=]+)"""),
        ) { it.groupValues[1] + "[Base64 data omitted]" }
            .replace(
                Regex("""(?i)(["']data["']\s*:\s*["'])[A-Za-z0-9+/=\\\r\n]{64,}(["'])"""),
            ) { it.groupValues[1] + "[Base64 data omitted]" + it.groupValues[2] }
            .replace(Regex("""(?i)data:[^,\s]+;base64,[^\s"'<>]+"""), "[Base64 data omitted]")
            .replace(Regex("(?:iVBORw0KGgo|/9j/|UklGR|R0lGOD)[A-Za-z0-9+/=]+"), "[image data omitted]")
    val continuation = Regex("""^[ \t]*[A-Za-z0-9+/]+={0,2}(?=[ \t]*$|["'<>])""")
    val lines =
        buildList {
            var omittingImage = false
            for (line in text.lineSequence()) {
                // shortcut: unlabelled short unpadded Base64 is ambiguous; preserve it until providers supply structured fields.
                val payload =
                    if (omittingImage) {
                        continuation.find(line)?.takeIf {
                            val value = it.value.trim()
                            value.length >= 64 || (value.endsWith('=') && value.length % 4 == 0)
                        }
                    } else {
                        null
                    }
                val remaining = payload?.let { line.substring(it.range.last + 1) } ?: line
                if (payload != null && remaining.isBlank()) continue
                add(remaining)
                omittingImage =
                    remaining.trimEnd().let { it.endsWith("[Base64 data omitted]") || it.endsWith("[image data omitted]") }
            }
        }.joinToString("\n")
            .filter { !it.isISOControl() || it == '\n' || it == '\t' }
            .trim()
    return if (lines.length > 8_000) lines.take(8_000) + "\n[truncated]" else lines
}
