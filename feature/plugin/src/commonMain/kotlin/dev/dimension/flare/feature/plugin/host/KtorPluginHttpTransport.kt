package dev.dimension.flare.feature.plugin.host

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.cancel
import io.ktor.utils.io.core.remaining
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import okio.Buffer
import okio.buffer
import okio.use

public class KtorPluginHttpTransport(
    engine: HttpClientEngine,
) : PluginHttpTransport,
    AutoCloseable {
    private val client =
        HttpClient(engine) {
            followRedirects = false
            expectSuccess = false
        }

    override suspend fun execute(request: PluginTransportRequestV1): PluginTransportResponseV1 =
        withTimeout(request.timeoutMillis) {
            val response =
                client.request(request.url) {
                    method = HttpMethod.parse(request.method)
                    request.headers.forEach { (name, value) -> headers.append(name, value) }
                    request.body?.let { setBody(it.toOutgoingContent()) }
                }
            val length = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            require(length == null || length <= MAX_RESPONSE_BYTES) { "HTTP response is too large" }
            val channel = response.bodyAsChannel()
            val packet = channel.readRemaining(MAX_RESPONSE_BYTES.toLong() + 1)
            require(packet.remaining <= MAX_RESPONSE_BYTES) {
                channel.cancel()
                "HTTP response is too large"
            }
            PluginTransportResponseV1(
                status = response.status.value,
                headers = response.headers.entries().associate { it.key to it.value },
                body = packet.readByteArray(),
            )
        }

    override fun close() {
        client.close()
    }
}

private fun PluginTransportBodyV1.toOutgoingContent(): OutgoingContent =
    when (this) {
        is PluginTransportBodyV1.Text -> {
            TextContent(value, ContentType.parse(contentType))
        }

        is PluginTransportBodyV1.Form -> {
            FormDataContent(
                Parameters.build {
                    values.forEach { (key, value) -> append(key, value) }
                },
            )
        }

        is PluginTransportBodyV1.Multipart -> {
            StreamingMultipartContent(parts)
        }
    }

private class StreamingMultipartContent(
    private val parts: List<PluginTransportMultipartPartV1>,
    private val boundary: String = "flare-${platformUuid()}",
) : OutgoingContent.WriteChannelContent() {
    override val contentType: ContentType = ContentType.MultiPart.FormData.withParameter("boundary", boundary)

    @OptIn(InternalAPI::class)
    override suspend fun writeTo(channel: ByteWriteChannel) {
        parts.forEach { part ->
            channel.writeUtf8("--$boundary\r\n")
            when (part) {
                is PluginTransportMultipartPartV1.Text -> {
                    channel.writeUtf8("Content-Disposition: form-data; name=\"${part.name}\"\r\n")
                    part.contentType?.let { channel.writeUtf8("Content-Type: $it\r\n") }
                    channel.writeUtf8("\r\n${part.value}\r\n")
                }

                is PluginTransportMultipartPartV1.Asset -> {
                    channel.writeUtf8(
                        "Content-Disposition: form-data; name=\"${part.name}\"; filename=\"${part.fileName}\"\r\n",
                    )
                    channel.writeUtf8("Content-Type: ${part.contentType}\r\n\r\n")
                    var copied = 0L
                    part.value.openSource().buffer().use { source ->
                        val buffer = Buffer()
                        while (true) {
                            val read = source.read(buffer, COPY_BUFFER_BYTES)
                            if (read == -1L) break
                            copied += read
                            require(copied <= part.value.size) { "Asset is larger than declared" }
                            channel.writeFully(buffer.readByteArray(read))
                        }
                    }
                    require(copied == part.value.size) { "Asset size changed during upload" }
                    channel.writeUtf8("\r\n")
                }
            }
        }
        channel.writeUtf8("--$boundary--\r\n")
    }
}

private suspend fun ByteWriteChannel.writeUtf8(value: String) {
    writeFully(value.encodeToByteArray())
}

private const val COPY_BUFFER_BYTES = 8_192L
