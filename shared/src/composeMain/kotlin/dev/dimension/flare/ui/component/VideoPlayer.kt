package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.ui.model.UiMedia

@Composable
expect fun VideoPlayer(
    data: UiMedia.Video,
    modifier: Modifier = Modifier,
    keepAspectRatio: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
    videoAutoplay: VideoAutoplay = VideoAutoplay.WIFI,
)

@Composable
expect fun GifPlayer(
    data: UiMedia.Gif,
    modifier: Modifier = Modifier,
    keepAspectRatio: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
)

@Composable
expect fun AudioPlayer(
    data: UiMedia.Audio,
    modifier: Modifier = Modifier,
)
