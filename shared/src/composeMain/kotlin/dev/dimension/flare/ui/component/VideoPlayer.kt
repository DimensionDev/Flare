package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import co.touchlab.compose.swift.bridge.ExpectSwiftView
import dev.dimension.flare.ui.model.UiMedia

@ExpectSwiftView
@Composable
expect fun VideoPlayer(
    data: UiMedia.Video,
    modifier: Modifier = Modifier,
)

@ExpectSwiftView
@Composable
expect fun GifPlayer(
    data: UiMedia.Gif,
    modifier: Modifier = Modifier,
)

@ExpectSwiftView
@Composable
expect fun AudioPlayer(
    data: UiMedia.Audio,
    modifier: Modifier = Modifier,
)
