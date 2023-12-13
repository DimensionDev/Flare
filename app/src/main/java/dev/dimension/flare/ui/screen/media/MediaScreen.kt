package dev.dimension.flare.ui.screen.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import com.mxalbert.zoomable.Zoomable
import com.ramcosta.composedestinations.annotation.DeepLink
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.spec.DestinationStyle
import dev.dimension.flare.ui.common.FullScreenBox
import dev.dimension.flare.ui.theme.FlareTheme

object FullScreenDialogStyle : DestinationStyle.Dialog {
    override val properties =
        DialogProperties(
            usePlatformDefaultWidth = false,
        )
}

@Composable
@Destination(
    style = FullScreenDialogStyle::class,
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
    ],
)
fun MediaRoute(
    uri: String,
    navigator: DestinationsNavigator,
) {
    MediaScreen(
        uri = uri,
        onDismiss = navigator::navigateUp,
    )
}

@Composable
internal fun MediaScreen(
    uri: String,
    onDismiss: () -> Unit,
) {
    FlareTheme(
        darkTheme = true,
    ) {
        FullScreenBox(
            modifier =
                Modifier
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f)),
        ) {
            Zoomable(
                dismissGestureEnabled = true,
                onDismiss = {
                    onDismiss.invoke()
                    true
                },
            ) {
                val painter =
                    rememberAsyncImagePainter(
                        model =
                            ImageRequest.Builder(LocalContext.current)
                                .data(uri)
                                .size(Size.ORIGINAL)
                                .build(),
                    )
                if (painter.state is AsyncImagePainter.State.Success) {
                    val size = painter.intrinsicSize
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .aspectRatio(size.width / size.height)
                                .fillMaxSize(),
                    )
                }
            }
        }
    }
}
