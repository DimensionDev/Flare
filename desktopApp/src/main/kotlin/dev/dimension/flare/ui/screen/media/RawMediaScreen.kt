package dev.dimension.flare.ui.screen.media

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun RawMediaScreen(url: String) {
    ImageItem(
        url = url,
        previewUrl = url,
        description = null,
        setLockPager = {},
        isFocused = true,
        modifier = Modifier.fillMaxSize(),
    )
}
