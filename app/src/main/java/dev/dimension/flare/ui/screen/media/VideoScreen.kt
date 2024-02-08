package dev.dimension.flare.ui.screen.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.ramcosta.composedestinations.annotation.DeepLink
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.ui.component.VideoPlayer
import dev.dimension.flare.ui.theme.FlareTheme
import moe.tlaster.swiper.Swiper
import moe.tlaster.swiper.rememberSwiperState

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
    SetDialogDestinationToEdgeToEdge()
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
        val swiperState =
            rememberSwiperState(
                onDismiss = onDismiss,
            )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 1 - swiperState.progress))
                    .alpha(1 - swiperState.progress),
        ) {
            Swiper(state = swiperState) {
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
