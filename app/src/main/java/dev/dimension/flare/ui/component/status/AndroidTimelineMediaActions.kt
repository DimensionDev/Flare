package dev.dimension.flare.ui.component.status

import android.content.ClipData
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.dimension.flare.R
import dev.dimension.flare.common.AndroidDownloadManager
import dev.dimension.flare.common.MediaFileNamePolicy
import dev.dimension.flare.common.shareImageMedia
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
internal fun ProvideAndroidTimelineMediaActions(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val downloadManager = koinInject<AndroidDownloadManager>()
    val mediaLinkLabel = stringResource(R.string.media_menu_media_link)
    val config =
        remember(context, clipboard, scope, downloadManager, mediaLinkLabel) {
            TimelineMediaActionConfig(
                showShareImage = true,
                handler =
                    TimelineMediaActionHandler { post, media, action ->
                        when (action) {
                            TimelineMediaMenuAction.Download -> {
                                scope.downloadMedia(
                                    context = context,
                                    downloadManager = downloadManager,
                                    post = post,
                                    media = media,
                                )
                            }

                            TimelineMediaMenuAction.DownloadAll -> {
                                scope.downloadAllMedia(
                                    context = context,
                                    downloadManager = downloadManager,
                                    post = post,
                                )
                            }

                            TimelineMediaMenuAction.ShareImage -> {
                                if (media is UiMedia.Image) {
                                    scope.launch {
                                        shareImageMedia(
                                            context = context,
                                            media = media,
                                            fileName = post.statusMediaFileName(media),
                                        )
                                    }
                                }
                            }

                            TimelineMediaMenuAction.CopyLink -> {
                                scope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(
                                            ClipData.newPlainText(
                                                mediaLinkLabel,
                                                media.url,
                                            ),
                                        ),
                                    )
                                }
                            }
                        }
                    },
            )
        }
    CompositionLocalProvider(LocalTimelineMediaActionConfig provides config) {
        content()
    }
}

private fun CoroutineScope.downloadMedia(
    context: Context,
    downloadManager: AndroidDownloadManager,
    post: UiTimelineV2.Post,
    media: UiMedia,
) {
    launch {
        runCatching {
            downloadManager.downloadMedia(
                uri = media.url,
                fileName = post.statusMediaFileName(media),
                customHeaders = media.customHeaders,
                callback =
                    object : AndroidDownloadManager.DownloadCallback {
                        override fun onDownloadStarted(downloadId: Long) {
                            context.showTimelineMediaToast(R.string.media_download_started)
                        }

                        override fun onDownloadSuccess(downloadId: Long) {
                            context.showTimelineMediaToast(R.string.media_save_success)
                        }

                        override fun onDownloadFailed(downloadId: Long) {
                            context.showTimelineMediaToast(R.string.media_save_fail)
                        }
                    },
            )
        }.onFailure {
            withContext(Dispatchers.Main) {
                context.showTimelineMediaToast(R.string.media_save_fail)
            }
        }
    }
}

private fun CoroutineScope.downloadAllMedia(
    context: Context,
    downloadManager: AndroidDownloadManager,
    post: UiTimelineV2.Post,
) {
    launch {
        context.showTimelineMediaToast(R.string.media_download_started)
        val result =
            runCatching {
                downloadManager.downloadAllMedia(
                    MediaFileNamePolicy.statusMediaFileNames(
                        statusKey = post.statusKey.toString(),
                        userHandle = post.user?.handle?.canonical ?: "unknown",
                        medias = post.images,
                    ),
                )
            }.getOrNull()
        withContext(Dispatchers.Main) {
            context.showTimelineMediaToast(
                if (result != null && result.failedFileNames.isEmpty()) {
                    R.string.media_save_success
                } else {
                    R.string.media_save_fail
                },
            )
        }
    }
}

private fun UiTimelineV2.Post.statusMediaFileName(media: UiMedia): String =
    MediaFileNamePolicy.statusMediaFileName(
        statusKey = statusKey.toString(),
        userHandle = user?.handle?.canonical ?: "unknown",
        media = media,
    )

private fun Context.showTimelineMediaToast(messageRes: Int) {
    Toast
        .makeText(this, getString(messageRes), Toast.LENGTH_SHORT)
        .show()
}
