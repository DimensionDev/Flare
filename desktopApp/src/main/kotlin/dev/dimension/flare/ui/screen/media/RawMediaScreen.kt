package dev.dimension.flare.ui.screen.media

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.dimension.flare.ui.component.MediaViewerPlayback
import dev.dimension.flare.ui.model.UiMedia

@Composable
internal fun RawMediaScreen(
    media: UiMedia?,
) {
    MediaViewerPlayback {
        ImageItem(
            media = media,
            description = null,
            setLockPager = {},
            isFocused = true,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
