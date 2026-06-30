package dev.dimension.flare.ui.component.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.Res
import dev.dimension.flare.common.DesktopDownloadManager
import dev.dimension.flare.common.DesktopSaveDialog
import dev.dimension.flare.common.MediaFileNamePolicy
import dev.dimension.flare.media_download_started
import dev.dimension.flare.media_save_fail
import dev.dimension.flare.media_save_success
import dev.dimension.flare.ui.component.ComposeInAppNotification
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.theme.LocalComposeWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
internal fun ProvideDesktopTimelineMediaActions(content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val window = LocalComposeWindow.current
    val downloadManager = koinInject<DesktopDownloadManager>()
    val notification = koinInject<ComposeInAppNotification>()
    val config =
        remember(scope, window, downloadManager, notification) {
            TimelineMediaActionConfig(
                showShareImage = false,
                handler =
                    TimelineMediaActionHandler { post, media, action ->
                        when (action) {
                            TimelineMediaMenuAction.Download -> {
                                val targetFile =
                                    DesktopSaveDialog.chooseFile(
                                        window = window,
                                        defaultName = post.statusMediaFileName(media),
                                    ) ?: return@TimelineMediaActionHandler
                                scope.launch {
                                    downloadManager.download(
                                        url = media.url,
                                        targetFile = targetFile,
                                        customHeaders = media.customHeaders,
                                    )
                                }
                            }

                            TimelineMediaMenuAction.DownloadAll -> {
                                val targetDirectory =
                                    DesktopSaveDialog.chooseDirectory(window = window)
                                        ?: return@TimelineMediaActionHandler
                                scope.downloadAllMedia(
                                    downloadManager = downloadManager,
                                    notification = notification,
                                    post = post,
                                    targetDirectory = targetDirectory,
                                )
                            }

                            TimelineMediaMenuAction.ShareImage -> {
                                Unit
                            }

                            TimelineMediaMenuAction.CopyLink -> {
                                Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                    StringSelection(media.url),
                                    null,
                                )
                            }
                        }
                    },
            )
        }
    CompositionLocalProvider(LocalTimelineMediaActionConfig provides config) {
        content()
    }
}

private fun CoroutineScope.downloadAllMedia(
    downloadManager: DesktopDownloadManager,
    notification: ComposeInAppNotification,
    post: UiTimelineV2.Post,
    targetDirectory: java.io.File,
) {
    launch {
        notification.message(Res.string.media_download_started)
        val result =
            runCatching {
                downloadManager.downloadAll(
                    mediaByFileName =
                        MediaFileNamePolicy.statusMediaFileNames(
                            statusKey = post.statusKey.toString(),
                            userHandle = post.user?.handle?.canonical ?: "unknown",
                            medias = post.images,
                        ),
                    targetDirectory = targetDirectory,
                )
            }.getOrNull()
        if (result != null && result.failedFileNames.isEmpty()) {
            notification.message(Res.string.media_save_success)
        } else {
            notification.message(Res.string.media_save_fail, success = false)
        }
    }
}

private fun UiTimelineV2.Post.statusMediaFileName(media: UiMedia): String =
    MediaFileNamePolicy.statusMediaFileName(
        statusKey = statusKey.toString(),
        userHandle = user?.handle?.canonical ?: "unknown",
        media = media,
    )
