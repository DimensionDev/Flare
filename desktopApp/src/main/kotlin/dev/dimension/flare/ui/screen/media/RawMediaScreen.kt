package dev.dimension.flare.ui.screen.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.dimension.flare.common.DesktopDownloadManager
import dev.dimension.flare.common.DesktopSaveDialog
import dev.dimension.flare.common.MediaFileNamePolicy
import dev.dimension.flare.ui.component.MediaViewerPlayback
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.media.ImageTranslationPresenter
import dev.dimension.flare.ui.screen.settings.imageTranslationText
import dev.dimension.flare.ui.theme.LocalComposeWindow
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
internal fun RawMediaScreen(media: UiMedia?) {
    val translation = remember { ImageTranslationPresenter() }.invoke()
    val downloader = koinInject<DesktopDownloadManager>()
    val scope = koinInject<CoroutineScope>()
    val window = LocalComposeWindow.current
    MediaViewerPlayback {
        Box(Modifier.fillMaxSize()) {
            ImageItem(
                translating = media?.let { translation.item(it).loading } == true,
                media = media?.let { translation.displayed(it) },
                onLoadError = {
                    if (media != null) {
                        val result = translation.displayed(media)
                        if (result.url != media.url) translation.imageFailed(media, result.url)
                    }
                },
                description = media?.description,
                setLockPager = {},
                isFocused = true,
                modifier = Modifier.fillMaxSize(),
            )
            if (media != null) {
                Row(Modifier.align(Alignment.BottomCenter)) {
                    ImageTranslationActions(translation, media)
                    if (translation.item(media).translated != null) {
                        SubtleButton(onClick = {
                            val displayed =
                                dev.dimension.flare.common.prepareImageTranslationExport(
                                    translation.displayed(media),
                                    dev.dimension.flare.common.FileSystemUtilsExt
                                        .flareCacheDirectory(),
                                )
                            val file = DesktopSaveDialog.chooseFile(window, MediaFileNamePolicy.rawMediaFileName(displayed))
                            if (file != null) scope.launch { downloader.download(displayed, file) }
                        }) { Text(imageTranslationText("Save")) }
                    }
                }
            }
        }
    }
}
