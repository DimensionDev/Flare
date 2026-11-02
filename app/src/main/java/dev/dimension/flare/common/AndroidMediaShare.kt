package dev.dimension.flare.common

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import coil3.annotation.ExperimentalCoilApi
import coil3.imageLoader
import dev.dimension.flare.R
import dev.dimension.flare.data.network.applyMedia
import dev.dimension.flare.data.network.mediaHttpClient
import dev.dimension.flare.ui.model.UiMedia
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalCoilApi::class)
internal suspend fun shareImageMedia(
    context: Context,
    media: UiMedia.Image,
    fileName: String,
) {
    val targetFile =
        File(
            context.cacheDir,
            MediaFileNamePolicy.safeLocalFileName(fileName, fallback = "media"),
        )
    val success =
        withContext(Dispatchers.IO) {
            if (media.url.startsWith("file://")) {
                if (!isImageTranslationFile(media, context.cacheDir)) return@withContext false
                return@withContext runCatching {
                    File(java.net.URI(media.url)).copyTo(targetFile, overwrite = true)
                    true
                }.getOrDefault(false)
            }
            context.imageLoader.diskCache?.openSnapshot(media.cacheKey)?.use {
                it.data.toFile().copyTo(targetFile, overwrite = true)
                true
            } ?: run {
                withContext(Dispatchers.Main) {
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.media_prepare_started),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
                downloadToFile(
                    media = media,
                    targetFile = targetFile,
                )
            }
        }

    withContext(Dispatchers.Main) {
        if (success) {
            val uri =
                FileProvider.getUriForFile(
                    context,
                    context.packageName + ".provider",
                    targetFile,
                )
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, uri)
                    setDataAndType(uri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(
                Intent.createChooser(
                    intent,
                    context.getString(R.string.media_menu_share_image),
                ),
            )
        } else {
            Toast
                .makeText(
                    context,
                    context.getString(R.string.media_save_fail),
                    Toast.LENGTH_SHORT,
                ).show()
        }
    }
}

internal suspend fun downloadToFile(
    media: UiMedia.Image,
    targetFile: File,
    client: HttpClient = mediaHttpClient(media.proxy),
): Boolean =
    runCatching {
        targetFile.parentFile?.mkdirs()
        try {
            client.prepareGet(media.url) { applyMedia(media) }.execute { response ->
                check(response.status.isSuccess())
                targetFile.outputStream().use { output ->
                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = channel.readAvailable(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    channel.closedCause?.let { throw it }
                }
            }
        } finally {
            client.close()
            client.engine.close()
        }
        true
    }.getOrElse {
        targetFile.delete()
        false
    }
