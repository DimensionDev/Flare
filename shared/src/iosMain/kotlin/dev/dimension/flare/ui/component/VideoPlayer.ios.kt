package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.ui.model.UiMedia

@Composable
actual fun VideoPlayer(
    data: UiMedia.Video,
    modifier: Modifier,
    keepAspectRatio: Boolean,
    contentScale: ContentScale,
    videoAutoplay: VideoAutoplay,
) {
}

@Composable
actual fun GifPlayer(
    data: UiMedia.Gif,
    modifier: Modifier,
    keepAspectRatio: Boolean,
    contentScale: ContentScale,
) {
}

@Composable
actual fun AudioPlayer(
    data: UiMedia.Audio,
    modifier: Modifier,
) {
}
