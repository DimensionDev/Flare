package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import dev.dimension.flare.ui.component.VideoPlayer

@Composable
internal actual fun PlatformVideoPlayer(
    uri: String,
    previewUri: String?,
    contentDescription: String?,
    modifier: Modifier,
    muted: Boolean,
    showControls: Boolean,
    keepScreenOn: Boolean,
    aspectRatio: Float?,
    contentScale: ContentScale,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    autoPlay: Boolean,
    remainingTimeContent: @Composable (BoxScope.(Long) -> Unit)?,
    loadingPlaceholder: @Composable BoxScope.() -> Unit,
) {
    VideoPlayer(
        uri = uri,
        previewUri = previewUri,
        contentDescription = contentDescription,
        modifier = modifier,
        muted = muted,
        showControls = showControls,
        keepScreenOn = keepScreenOn,
        aspectRatio = aspectRatio,
        contentScale = contentScale,
        onClick = onClick,
        onLongClick = onLongClick,
        autoPlay = autoPlay,
        remainingTimeContent = remainingTimeContent,
        loadingPlaceholder = loadingPlaceholder,
    )
}
