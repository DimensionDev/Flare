package dev.dimension.flare.ui.screen.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.ramcosta.composedestinations.annotation.DeepLink
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.VideoPlayer
import dev.dimension.flare.ui.component.status.UiStatusQuoted
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.medias
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.StatusPresenter
import dev.dimension.flare.ui.presenter.status.StatusState
import dev.dimension.flare.ui.screen.destinations.StatusRouteDestination
import dev.dimension.flare.ui.theme.FlareTheme
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import moe.tlaster.swiper.Swiper
import moe.tlaster.swiper.rememberSwiperState

@Composable
@Destination(
    style = FullScreenDialogStyle::class,
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
    ],
)
fun StatusMediaRoute(
    statusKey: MicroBlogKey,
    index: Int,
    navigator: DestinationsNavigator,
) {
    SetDialogDestinationToEdgeToEdge()
    StatusMediaScreen(
        statusKey = statusKey,
        index = index,
        onDismiss = navigator::navigateUp,
        toStatus = {
            navigator.navigate(StatusRouteDestination(statusKey))
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StatusMediaScreen(
    statusKey: MicroBlogKey,
    index: Int,
    toStatus: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state by producePresenter {
        statusMediaPresenter(statusKey)
    }

    FlareTheme(darkTheme = true) {
        val swiperState =
            rememberSwiperState(
                onDismiss = onDismiss,
            )
        Box(
            modifier =
                Modifier
//                .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 1 - swiperState.progress))
                    .alpha(1 - swiperState.progress),
        ) {
            Swiper(state = swiperState) {
                state.medias.onSuccess { medias ->
                    HorizontalPager(
                        state =
                            rememberPagerState(
                                initialPage = index,
                                pageCount = {
                                    medias.size
                                },
                            ),
                    ) {
                        when (val media = medias[it]) {
                            is UiMedia.Audio ->
                                VideoPlayer(
                                    uri = media.url,
                                    previewUri = null,
                                    contentDescription = media.description,
                                    modifier = Modifier.fillMaxSize(),
                                )

                            is UiMedia.Gif ->
                                VideoPlayer(
                                    uri = media.url,
                                    previewUri = media.previewUrl,
                                    contentDescription = media.description,
                                    modifier = Modifier.fillMaxSize(),
                                    aspectRatio = media.aspectRatio,
                                )

                            is UiMedia.Image ->
                                ZoomableAsyncImage(
                                    model = media.url,
                                    contentDescription = media.description,
                                    modifier = Modifier.fillMaxSize(),
                                )

                            is UiMedia.Video ->
                                VideoPlayer(
                                    uri = media.url,
                                    previewUri = media.thumbnailUrl,
                                    contentDescription = media.description,
                                    modifier = Modifier.fillMaxSize(),
                                    aspectRatio = media.aspectRatio,
                                    showControls = true,
                                    keepScreenOn = true,
                                    muted = false,
                                )
                        }
                    }
                }
            }
            state.status.onSuccess { status ->
                AnimatedVisibility(
                    visible = swiperState.progress == 0f,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter),
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) {
                    UiStatusQuoted(
                        status = status,
                        onMediaClick = {},
                        onClick = toStatus,
                        showMedia = false,
                        modifier =
                            Modifier
                                .systemBarsPadding(),
                    )
                }
            }
        }
    }
}

@Composable
private fun statusMediaPresenter(statusKey: MicroBlogKey) =
    run {
        val state =
            remember(statusKey) {
                StatusPresenter(statusKey)
            }.invoke()
        val medias =
            state.status.map {
                it.medias
            }
        object : StatusState by state {
            val medias = medias
        }
    }
