package dev.dimension.flare.data.network.ai

import dev.dimension.flare.common.MimeTypes
import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.common.UploadMedia
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.network.applyMedia
import dev.dimension.flare.data.network.mediaHttpClient
import dev.dimension.flare.shared.image.ImageCompressor
import dev.dimension.flare.shared.image.imageMetadata
import dev.dimension.flare.ui.model.UiMedia
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Url
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray
import okio.Buffer
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public data class PreparedAiImage(
    public val bytes: ByteArray,
    public val mimeType: String,
)

@HiddenFromObjC
public class AiImagePreparer(
    private val compressor: ImageCompressor,
    private val clientFactory: (ProxyRef) -> HttpClient = ::mediaHttpClient,
) {
    public suspend fun prepare(
        media: UiMedia.Image,
        maxBytes: Int,
    ): PreparedAiImage {
        val url = Url(media.url)
        require(url.protocol.name in setOf("http", "https") && url.user.isNullOrEmpty() && url.password.isNullOrEmpty())
        val bytes =
            withTimeoutOrNull(30_000) {
                val client = clientFactory(media.proxy)
                try {
                    client.prepareGet(media.url) { applyMedia(media) }.execute { response ->
                        check(response.status.isSuccess()) { "Image download failed: HTTP ${response.status.value}" }
                        check((response.contentLength() ?: 0) <= MAX_DOWNLOAD_BYTES) { "Image download exceeds safety limit" }
                        val channel = response.bodyAsChannel()

                        // Ktor versions across targets share this bounded-read API.
                        @Suppress("DEPRECATION")
                        val data = channel.readRemaining(MAX_DOWNLOAD_BYTES + 1L).readByteArray()
                        channel.closedCause?.let { throw it }
                        check(data.isNotEmpty() && data.size <= MAX_DOWNLOAD_BYTES) { "Image download exceeds safety limit" }
                        data
                    }
                } finally {
                    client.close()
                    client.engine.close()
                }
            } ?: error("Image download timed out")
        return prepare(bytes, maxBytes)
    }

    public suspend fun prepare(
        media: UploadMedia,
        maxBytes: Int = MAX_IMAGE_BYTES,
    ): PreparedAiImage {
        check(media.size in 1..MAX_DOWNLOAD_BYTES.toLong()) { "Image exceeds preparation safety limit" }
        val buffer = Buffer()
        media.forEachChunk { bytes, count -> buffer.write(bytes, 0, count) }
        return prepare(buffer.readByteArray(), maxBytes)
    }

    private suspend fun prepare(
        bytes: ByteArray,
        maxBytes: Int,
    ): PreparedAiImage =
        withContext(PlatformDispatchers.IO) {
            require(maxBytes > 0)
            require(MimeTypes.detectFromBytes(bytes) in SUPPORTED_TYPES) { "Unsupported image data" }
            val metadata = requireNotNull(imageMetadata(bytes)) { "Invalid image data" }
            check(metadata.width > 0 && metadata.height > 0 && metadata.width.toLong() * metadata.height <= MAX_PIXELS) {
                "Image exceeds pixel safety limit"
            }
            val prepared =
                if (bytes.size > maxBytes || metadata.width > MAX_DIMENSION || metadata.height > MAX_DIMENSION) {
                    check(!metadata.animated) { "Cannot resize an animated image without losing frames" }
                    compressor.compress(bytes, maxBytes.toLong(), MAX_DIMENSION to MAX_DIMENSION)
                } else {
                    bytes
                }
            check(prepared.isNotEmpty() && prepared.size <= maxBytes) { "Compressed image exceeds request budget" }
            val resultMetadata = requireNotNull(imageMetadata(prepared)) { "Invalid compressed image" }
            check(
                resultMetadata.width in 1..MAX_DIMENSION && resultMetadata.height in 1..MAX_DIMENSION,
            ) { "Compressed image exceeds dimension limit" }
            val mimeType = requireNotNull(MimeTypes.detectFromBytes(prepared)).also { require(it in SUPPORTED_TYPES) }
            PreparedAiImage(prepared, mimeType)
        }

    public companion object {
        public const val MAX_IMAGE_BYTES: Int = 4 * 1024 * 1024
        private const val MAX_DOWNLOAD_BYTES = 64 * 1024 * 1024
        private const val MAX_PIXELS = 64_000_000L
        private const val MAX_DIMENSION = 4096
        private val SUPPORTED_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
    }
}
