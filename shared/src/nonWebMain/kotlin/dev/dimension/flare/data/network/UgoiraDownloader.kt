package dev.dimension.flare.data.network

import dev.dimension.flare.media.UgoiraFrame
import dev.dimension.flare.media.UgoiraMetadata
import dev.dimension.flare.ui.model.UiMedia
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Url
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.io.IOException
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.openZip
import okio.use

/** Pixiv source selection and bounded transfers. No credentials or binary response logging. */
internal class UgoiraDownloader(
    private val fileSystem: FileSystem,
    private val client: HttpClient,
) {
    suspend fun download(
        media: UiMedia.Ugoira,
        metadata: UgoiraMetadata,
        directory: Path,
        onProgress: (Float) -> Unit,
    ): UgoiraDownload {
        metadata.validate()
        validateUrl(metadata.zipUrl)
        val original = media.originalFrameUrl?.takeIf { ORIGINAL_FRAME.matches(it) }
        val base = (original ?: metadata.zipUrl.replace("/img-zip-ugoira/", "/img-original/")).substringBeforeLast("_ugoira") + "_ugoira"
        val extensions = original?.substringAfterLast('.')?.let(::listOf) ?: listOf("jpg", "png", "gif")
        for (extension in extensions) {
            try {
                var bytes = 0L
                val frames =
                    metadata.frames.mapIndexed { index, frame ->
                        val name = "$index.$extension"
                        bytes += downloadFile("$base$index.$extension", directory / name, FRAME_LIMIT)
                        require(bytes <= SEQUENCE_LIMIT) { "Ugoira exceeds the size limit" }
                        onProgress((index + 1f) / metadata.frames.size)
                        UgoiraFrame(name, frame.delayMillis)
                    }
                return UgoiraDownload(frames, "original")
            } catch (_: SourceUnavailable) {
                discardSource(directory, onProgress)
            }
        }
        val largeZip = metadata.zipUrl.replace("_ugoira600x600", "_ugoira1920x1080")
        for (url in listOf(largeZip, metadata.zipUrl).distinct()) {
            try {
                val zip = directory / "frames.zip"
                downloadFile(url, zip, SEQUENCE_LIMIT)
                val frames = extractFrames(zip, metadata.frames, directory, onProgress)
                fileSystem.delete(zip)
                return UgoiraDownload(frames, if (url != metadata.zipUrl) "large" else "medium")
            } catch (_: SourceUnavailable) {
                discardSource(directory, onProgress)
            }
        }
        error("Ugoira source is unavailable")
    }

    private fun extractFrames(
        zip: Path,
        frames: List<UgoiraFrame>,
        directory: Path,
        onProgress: (Float) -> Unit,
    ): List<UgoiraFrame> =
        fileSystem.openZip(zip).use { archive ->
            var bytes = 0L
            frames.mapIndexed { index, frame ->
                val source = "/${frame.file}".toPath()
                val size = requireNotNull(archive.metadata(source).size)
                require(size in 1..FRAME_LIMIT && bytes + size <= SEQUENCE_LIMIT) { "Ugoira archive exceeds the size limit" }
                val name = "$index.${frame.file.substringAfterLast('.')}"
                archive.source(source).buffer().use { input ->
                    fileSystem.sink(directory / name).buffer().use { output -> output.writeAll(input) }
                }
                bytes += size
                onProgress((index + 1f) / frames.size)
                UgoiraFrame(name, frame.delayMillis)
            }
        }

    private fun discardSource(
        directory: Path,
        onProgress: (Float) -> Unit,
    ) {
        fileSystem.list(directory).forEach { fileSystem.delete(it) }
        onProgress(0f)
    }

    private suspend fun downloadFile(
        url: String,
        destination: Path,
        maximumBytes: Long,
    ): Long {
        validateUrl(url)
        return flow {
            val bytes =
                try {
                    client
                        .prepareGet(url) {
                            header("Referer", "https://www.pixiv.net/")
                        }.execute { response ->
                            when (response.status.value) {
                                200 -> Unit
                                403, 404, 410 -> throw SourceUnavailable()
                                429, in 500..599 -> throw IOException("Ugoira download failed: HTTP ${response.status.value}")
                                else -> error("Ugoira download failed: HTTP ${response.status.value}")
                            }
                            var total = 0L
                            val input = response.bodyAsChannel()
                            val buffer = ByteArray(64 * 1024)
                            fileSystem.sink(destination).buffer().use { output ->
                                while (true) {
                                    val count = input.readAvailable(buffer)
                                    if (count < 0) break
                                    total += count
                                    require(total <= maximumBytes) { "Ugoira resource exceeds the size limit" }
                                    output.write(buffer, 0, count)
                                }
                                input.closedCause?.let { throw it }
                            }
                            require(total > 0) { "Empty Ugoira resource" }
                            total
                        }
                } catch (cause: Exception) {
                    fileSystem.delete(destination, mustExist = false)
                    throw cause
                }
            emit(bytes)
        }.retryWhen { cause, attempt ->
            if (cause !is IOException || attempt >= 2) return@retryWhen false
            delay(500L * (attempt + 1))
            true
        }.first()
    }

    private fun validateUrl(value: String) {
        val url = Url(value)
        require(url.protocol.name == "https" && (url.host == "i.pximg.net" || url.host.endsWith(".pximg.net"))) {
            "Invalid Pixiv media URL"
        }
        require(url.user.isNullOrEmpty() && url.password.isNullOrEmpty()) { "Invalid Pixiv media URL" }
    }

    private class SourceUnavailable : Exception()

    companion object {
        const val FRAME_LIMIT = 64L * 1024 * 1024
        const val SEQUENCE_LIMIT = 512L * 1024 * 1024
        private val ORIGINAL_FRAME = Regex("https://[^/]+/img-original/.+_ugoira0\\.(jpg|png|gif)$")
    }
}

internal data class UgoiraDownload(
    val frames: List<UgoiraFrame>,
    val quality: String,
)

internal fun ugoiraHttpClient(): HttpClient =
    HttpClient(httpClientEngine) {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = 60_000
            requestTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
    }
