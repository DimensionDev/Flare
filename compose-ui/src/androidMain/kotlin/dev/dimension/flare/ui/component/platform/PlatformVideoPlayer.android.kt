package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import dev.dimension.flare.ui.component.VideoPlayer
import dev.dimension.flare.ui.model.UiMedia

@Composable
internal actual fun PlatformVideoPlayer(
    media: UiMedia,
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
    errorContent: @Composable BoxScope.() -> Unit,
    loadingPlaceholder: @Composable BoxScope.() -> Unit,
) {
    VideoPlayer(
        media = media,
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
