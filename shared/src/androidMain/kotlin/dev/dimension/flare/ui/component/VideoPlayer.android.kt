package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.dimension.flare.ui.model.UiMedia

@Composable
actual fun VideoPlayer(
    data: UiMedia.Video,
    modifier: Modifier,
) {
}

@Composable
actual fun GifPlayer(
    data: UiMedia.Gif,
    modifier: Modifier,
) {
}

@Composable
actual fun AudioPlayer(
    data: UiMedia.Audio,
    modifier: Modifier,
) {
}
