package dev.dimension.flare.ui.screen.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import com.mxalbert.zoomable.Zoomable
import com.ramcosta.composedestinations.annotation.DeepLink
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.FULL_ROUTE_PLACEHOLDER
import dev.dimension.flare.ui.theme.FlareTheme

@Composable
@Destination(
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER"
        )
    ]
)
fun MediaRoute(
    uri: String
) {
    MediaScreen(
        uri = uri
    )
}

@Composable
internal fun MediaScreen(
    uri: String
) {
    FlareTheme(
        darkTheme = true
    ) {
        Scaffold {
            Zoomable(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it)
            ) {
                val painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(uri)
                        .size(Size.ORIGINAL)
                        .build()
                )
                if (painter.state is AsyncImagePainter.State.Success) {
                    val size = painter.intrinsicSize
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier
                            .aspectRatio(size.width / size.height)
                            .fillMaxSize()
                    )
                }
            }
        }
    }
}
