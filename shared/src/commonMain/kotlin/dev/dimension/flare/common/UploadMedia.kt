package dev.dimension.flare.common

import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.shared.image.ImageCompressor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.Source
import okio.buffer
import okio.use
import kotlin.native.HiddenFromObjC

/** A reopenable file. Every consumer owns its reader; retries never reuse an exhausted stream. */
@HiddenFromObjC
public class UploadMedia private constructor(
    public val name: String,
    public val mimeType: String,
    public val size: Long,
    private val openSource: () -> Source,
) {
    public val isVideo: Boolean get() = mimeType.startsWith("video/")
    public val isGif: Boolean get() = mimeType == "image/gif"

    /** The array is reused. Consumers must finish using it before returning. */
    public suspend fun forEachChunk(
        chunkSize: Int = 64 * 1024,
        consume: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Unit =
        withContext(PlatformDispatchers.IO) {
            require(chunkSize > 0)
            openSource().buffer().use { source ->
                val bytes = ByteArray(chunkSize)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    // Fill a chunk even when the underlying source performs short reads.
                    var count = 0
                    while (count < bytes.size) {
                        currentCoroutineContext().ensureActive()
                        val read = source.read(bytes, count, bytes.size - count)
                        if (read == -1) break
                        count += read
                    }
                    if (count == 0) break
                    total += count
                    check(total <= size) { "Media changed while reading: $name" }
                    consume(bytes, count)
                }
                check(total == size) { "Media changed while reading: $name" }
            }
        }

    public fun validate(
        platform: String,
        acceptedTypes: Collection<String>? = null,
        maxBytes: Long? = null,
    ) {
        require(size > 0) { "$platform cannot upload an empty file: $name" }
        require(mimeType.startsWith("image/") || isVideo) { "$platform does not support $mimeType: $name" }
        require(acceptedTypes.isNullOrEmpty() || mimeType in acceptedTypes) { "$platform does not support $mimeType: $name" }
        require(maxBytes == null || size <= maxBytes) { "$platform media exceeds $maxBytes bytes: $name ($size bytes)" }
    }

    public suspend fun compressImage(
        compressor: ImageCompressor,
        compression: ComposeConfig.Media.Compression,
    ): UploadMedia {
        if (!mimeType.startsWith("image/") || isGif) return this
        return withContext(PlatformDispatchers.IO) {
            val bytes = openSource().buffer().use { it.readByteArray() }
            fromBytes(
                name,
                compressor.compress(bytes, compression.maxSizeBytes, compression.maxWidth to compression.maxHeight),
                mimeType,
            )
        }
    }

    public companion object {
        public suspend fun fromSource(
            name: String?,
            mimeType: String?,
            size: Long?,
            openSource: () -> Source,
        ): UploadMedia =
            withContext(PlatformDispatchers.IO) {
                val header =
                    openSource().buffer().use { source ->
                        source.request(64)
                        source.readByteArray(minOf(64, source.buffer.size))
                    }
                val resolvedType =
                    MimeTypes.detectFromBytes(header)
                        ?: mimeType
                            ?.substringBefore(';')
                            ?.trim()
                            ?.lowercase()
                            ?.takeUnless { it.isBlank() || it == "application/octet-stream" || it.endsWith("/*") }
                        ?: MimeTypes.fromFileName(name)
                        ?: "application/octet-stream"
                val length =
                    size?.takeIf { it >= 0 } ?: openSource().use { source ->
                        val buffer = Buffer()
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = source.read(buffer, 64 * 1024L)
                            if (count == -1L) break
                            total += count
                            buffer.clear()
                        }
                        total
                    }
                val safeName = MediaFileNamePolicy.sanitizeFileName(name, "upload")
                val extension = MimeTypes.extensionFor(resolvedType)
                val uploadName = if (extension != null) "${safeName.substringBeforeLast('.', safeName)}.$extension" else safeName
                UploadMedia(uploadName, resolvedType, length, openSource)
            }

        public suspend fun fromBytes(
            name: String?,
            bytes: ByteArray,
            mimeType: String? = null,
        ): UploadMedia = fromSource(name, mimeType, bytes.size.toLong()) { Buffer().write(bytes) }
    }
}
