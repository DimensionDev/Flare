package dev.dimension.flare.ui.screen.media

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.dimension.flare.ui.component.NetworkImage
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

@Composable
internal fun RawMediaScreen(url: String) {
    NetworkImage(
        modifier =
            Modifier
                .fillMaxSize()
                .zoomable(rememberZoomableState()),
        model = url,
        contentDescription = null,
    )
}
