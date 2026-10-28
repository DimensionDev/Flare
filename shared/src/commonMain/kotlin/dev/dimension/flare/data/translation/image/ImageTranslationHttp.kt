package dev.dimension.flare.data.translation.image

import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.datastore.model.isValidImageTranslationServiceUrl
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.network.ai.classifyAiImageFailure
import dev.dimension.flare.data.network.mediaHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Buffer

internal fun imageTranslationHttpClient(): HttpClient =
    mediaHttpClient(ProxyRef.Global).config {
        useDefaultTransformers = true
        followRedirects = false
        install(HttpTimeout) {
            requestTimeoutMillis = 180_000
            socketTimeoutMillis = 180_000
            connectTimeoutMillis = 30_000
        }
    }

internal fun checkedImageServiceUrl(value: String): String {
    if (!isValidImageTranslationServiceUrl(value)) {
        throw ImageTranslationException(ImageTranslationError.NotConfigured)
    }
    return value.trimEnd('/')
}

internal suspend fun HttpClient.imagePost(
    url: String,
    body: JsonObject,
    configure: HttpRequestBuilder.() -> Unit = {},
): JsonObject = imagePost(url, TextContent(body.toString(), ContentType.Application.Json), configure)

internal suspend fun HttpClient.imagePost(
    url: String,
    body: OutgoingContent,
    configure: HttpRequestBuilder.() -> Unit = {},
): JsonObject =
    preparePost(url) {
        setBody(body)
        configure()
    }.execute { response ->
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(64 * 1024)
        val payload = Buffer()
        while (true) {
            val read = channel.readAvailable(buffer)
            if (read < 0) break
            if (payload.size + read > MAX_IMAGE_JSON_BYTES) throw ImageTranslationException(ImageTranslationError.UnsupportedInput)
            payload.write(buffer, 0, read)
        }
        channel.closedCause?.let { throw it }
        val body = payload.readUtf8()
        val parsed = runCatching { JSON.parseToJsonElement(body) as? JsonObject }.getOrNull()
        if (response.status.value !in 200..299) {
            val error = parsed?.get("error")?.let { runCatching { it.jsonObject }.getOrNull() }
            val code = error?.text("code")
            val parameter = error?.text("param").orEmpty()
            throw ImageTranslationException(
                when (response.status.value) {
                    401, 403 -> ImageTranslationError.Authentication
                    429 -> ImageTranslationError.RateLimited
                    400, 413, 415, 422 -> ImageTranslationError.UnsupportedInput
                    else -> ImageTranslationError.Network
                },
                imageFailure = classifyAiImageFailure(response.status.value, body),
                retryWithFile =
                    response.status.value == 415 ||
                        (
                            response.status.value == 400 &&
                                parameter.startsWith("images") &&
                                code in setOf("unknown_parameter", "unsupported_parameter", "invalid_type")
                        ),
            )
        }
        parsed ?: throw ImageTranslationException(ImageTranslationError.NoImage)
    }

internal fun streamingImageBody(
    input: ImageTranslationInput,
    prefix: String,
    suffix: String,
    maxBytes: Long,
): OutgoingContent {
    if (input.size > maxBytes) throw ImageTranslationException(ImageTranslationError.UnsupportedInput)
    val head = prefix.encodeToByteArray()
    val tail = suffix.encodeToByteArray()
    return object : OutgoingContent.WriteChannelContent() {
        override val contentType = ContentType.Application.Json
        override val contentLength = head.size + ((input.size + 2) / 3) * 4 + tail.size

        override suspend fun writeTo(channel: ByteWriteChannel) {
            channel.writeFully(head)
            input.writeBase64(channel)
            channel.writeFully(tail)
        }
    }
}

internal fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.content?.takeUnless { it == "null" || it.isBlank() }

private const val MAX_IMAGE_JSON_BYTES = 64L * 1024 * 1024
