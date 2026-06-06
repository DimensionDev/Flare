package dev.dimension.flare.ui.screen.media

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableMap

@Composable
internal fun RawMediaScreen(
    url: String,
    customHeaders: ImmutableMap<String, String>?,
) {
    ImageItem(
        url = url,
        previewUrl = url,
        customHeaders = customHeaders,
        description = null,
        setLockPager = {},
        isFocused = true,
        modifier = Modifier.fillMaxSize(),
    )
}
