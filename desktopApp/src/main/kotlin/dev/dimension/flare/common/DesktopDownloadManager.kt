package dev.dimension.flare.common

import dev.dimension.flare.Res
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.media_save_fail
import dev.dimension.flare.media_save_success
import dev.dimension.flare.ui.component.ComposeInAppNotification
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class DesktopDownloadManager(
    private val inAppNotification: ComposeInAppNotification,
    private val client: HttpClient = ktorClient(),
) : Closeable {
    suspend fun download(
        url: String,
        targetFile: File,
        overwrite: Boolean = true,
        onProgress: (DownloadProgress) -> Unit = {},
    ): DownloadResult =
        withContext(Dispatchers.IO) {
            require(url.isNotBlank()) { "url must not be blank" }
            require(targetFile.path.isNotBlank()) { "targetFile must not be blank" }
            if (targetFile.exists() && !overwrite) {
                throw IllegalStateException("Target file already exists: ${targetFile.absolutePath}")
            }

            targetFile.parentFile?.mkdirs()
            val tempFile = File(targetFile.absolutePath + ".part")
            tempFile.parentFile?.mkdirs()
            if (tempFile.exists()) {
                tempFile.delete()
            }

            try {
                client.prepareGet(url).execute { response ->
                    writeResponseToFile(
                        response = response,
                        outputFile = tempFile,
                        onProgress = onProgress,
                    )
                }
                moveIntoPlace(tempFile = tempFile, targetFile = targetFile, overwrite = overwrite)
                inAppNotification.message(Res.string.media_save_success)
                DownloadResult(
                    url = url,
                    file = targetFile,
                    bytesWritten = targetFile.length(),
                )
            } catch (t: Throwable) {
                inAppNotification.message(Res.string.media_save_fail)
                tempFile.delete()
                throw t
            }
        }

    override fun close() {
        client.close()
    }
}

internal data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?,
) {
    val fraction: Float?
        get() =
            totalBytes
                ?.takeIf { it > 0 }
                ?.let { bytesDownloaded.toFloat() / it.toFloat() }
}

internal data class DownloadResult(
    val url: String,
    val file: File,
    val bytesWritten: Long,
)

private suspend fun writeResponseToFile(
    response: HttpResponse,
    outputFile: File,
    onProgress: (DownloadProgress) -> Unit,
) {
    val totalBytes = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    val channel = response.body<ByteReadChannel>()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var downloaded = 0L

    outputFile.outputStream().buffered().use { output ->
        while (true) {
            val read = channel.readAvailable(buffer)
            if (read <= 0) {
                break
            }
            output.write(buffer, 0, read)
            downloaded += read
            onProgress(
                DownloadProgress(
                    bytesDownloaded = downloaded,
                    totalBytes = totalBytes,
                ),
            )
        }
        output.flush()
    }
}

private fun moveIntoPlace(
    tempFile: File,
    targetFile: File,
    overwrite: Boolean,
) {
    try {
        if (overwrite) {
            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } else {
            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
        }
    } catch (_: AtomicMoveNotSupportedException) {
        if (overwrite) {
            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        } else {
            Files.move(tempFile.toPath(), targetFile.toPath())
        }
    }
}
