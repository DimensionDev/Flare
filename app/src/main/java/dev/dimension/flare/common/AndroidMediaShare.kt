package dev.dimension.flare.common

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import coil3.annotation.ExperimentalCoilApi
import coil3.imageLoader
import dev.dimension.flare.R
import dev.dimension.flare.ui.model.UiMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

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
            context.imageLoader.diskCache?.openSnapshot(media.url)?.use {
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
                    url = media.url,
                    targetFile = targetFile,
                    customHeaders = media.customHeaders,
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

private fun downloadToFile(
    url: String,
    targetFile: File,
    customHeaders: Map<String, String>?,
): Boolean =
    runCatching {
        targetFile.parentFile?.mkdirs()
        val connection = URL(url).openConnection()
        customHeaders?.forEach { (key, value) ->
            connection.setRequestProperty(key, value)
        }
        if (connection is HttpURLConnection) {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                return@runCatching false
            }
        }
        connection.getInputStream().use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (connection is HttpURLConnection) {
            connection.disconnect()
        }
        true
    }.getOrDefault(false)
