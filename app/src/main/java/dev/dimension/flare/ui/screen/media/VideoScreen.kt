package dev.dimension.flare.ui.screen.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mxalbert.zoomable.Zoomable
import com.ramcosta.composedestinations.annotation.DeepLink
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.ui.common.FullScreenBox
import dev.dimension.flare.ui.component.VideoPlayer
import dev.dimension.flare.ui.theme.FlareTheme

@Destination(
    style = FullScreenDialogStyle::class,
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
    ],
)
@Composable
internal fun VideoRoute(
    uri: String,
    previewUri: String?,
    contentDescription: String?,
    navigator: DestinationsNavigator,
) {
    VideoScreen(
        uri = uri,
        previewUri = previewUri,
        contentDescription = contentDescription,
        onDismiss = navigator::navigateUp,
    )
}

@Composable
internal fun VideoScreen(
    uri: String,
    previewUri: String?,
    contentDescription: String?,
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
                VideoPlayer(
                    uri = uri,
                    previewUri = previewUri,
                    contentDescription = contentDescription,
                    modifier =
                        Modifier
                            .fillMaxSize(),
                    showControls = true,
                    muted = false,
                )
            }
        }
    }
}
