package dev.dimension.flare.ui.screen.media

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import coil3.imageLoader
import coil3.request.ImageRequest
import com.eygraber.compose.placeholder.material3.placeholder
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.ramcosta.composedestinations.annotation.DeepLink
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.VideoPlayer
import dev.dimension.flare.ui.component.status.UiStatusQuoted
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.medias
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.StatusPresenter
import dev.dimension.flare.ui.presenter.status.StatusState
import dev.dimension.flare.ui.screen.destinations.StatusRouteDestination
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import moe.tlaster.swiper.Swiper
import moe.tlaster.swiper.rememberSwiperState
import org.koin.compose.koinInject

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
    preview: String?,
    navigator: DestinationsNavigator,
) {
    SetDialogDestinationToEdgeToEdge()
    StatusMediaScreen(
        statusKey = statusKey,
        index = index,
        onDismiss = navigator::navigateUp,
        preview = preview,
        toStatus = {
            navigator.navigate(StatusRouteDestination(statusKey))
        },
    )
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalPermissionsApi::class,
)
@Composable
internal fun StatusMediaScreen(
    statusKey: MicroBlogKey,
    index: Int,
    preview: String?,
    toStatus: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val permissionState =
        rememberPermissionState(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
    val state by producePresenter {
        statusMediaPresenter(statusKey, index, context)
    }
    BackHandler(state.showUi) {
        state.setShowUi(false)
    }
    FlareTheme(darkTheme = true) {
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
            Swiper(
                state = swiperState,
                enabled = !state.lockPager,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize(),
                ) {
                    state.medias.onSuccess { medias ->
                        val pagerState =
                            rememberPagerState(
                                initialPage = index,
                                pageCount = {
                                    medias.size
                                },
                            )
                        LaunchedEffect(pagerState.currentPage) {
                            state.setWithVideoPadding(medias[pagerState.currentPage] is UiMedia.Video)
                            state.setCurrentPage(pagerState.currentPage)
                        }
                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = !state.lockPager,
                        ) {
                            when (val media = medias[it]) {
                                is UiMedia.Audio ->
                                    VideoPlayer(
                                        uri = media.url,
                                        previewUri = null,
                                        contentDescription = media.description,
                                        modifier =
                                            Modifier
                                                .fillMaxSize(),
                                        onClick = {
                                            state.setShowUi(!state.showUi)
                                        },
                                        onLongClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            state.setShowMenu(true)
                                        },
                                    )

                                is UiMedia.Gif ->
                                    VideoPlayer(
                                        uri = media.url,
                                        previewUri = media.previewUrl,
                                        contentDescription = media.description,
                                        modifier =
                                            Modifier
                                                .fillMaxSize(),
                                        onClick = {
                                            state.setShowUi(!state.showUi)
                                        },
                                        aspectRatio = media.aspectRatio,
                                        onLongClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            state.setShowMenu(true)
                                        },
                                    )

                                is UiMedia.Image -> {
                                    val zoomableState =
                                        rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 10f))
                                    LaunchedEffect(zoomableState.zoomFraction) {
                                        zoomableState.zoomFraction?.let {
                                            state.setLockPager(it > 0.01f)
                                        } ?: state.setLockPager(false)
                                    }
                                    val painter =
                                        rememberAsyncImagePainter(
                                            model =
                                                ImageRequest.Builder(LocalContext.current)
                                                    .data(media.url)
                                                    .placeholderMemoryCacheKey(media.previewUrl)
                                                    .build(),
                                        )
                                    LaunchedEffect(painter.intrinsicSize) {
                                        zoomableState.setContentLocation(
                                            ZoomableContentLocation.scaledInsideAndCenterAligned(
                                                painter.intrinsicSize,
                                            ),
                                        )
                                    }
                                    Image(
                                        painter = painter,
                                        contentDescription = media.description,
                                        contentScale = ContentScale.Inside,
                                        alignment = Alignment.Center,
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .zoomable(
                                                    zoomableState,
                                                    onClick = {
                                                        state.setShowUi(!state.showUi)
                                                    },
                                                    onLongClick = {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        state.setShowMenu(true)
                                                    },
                                                ),
                                    )
                                }

                                is UiMedia.Video ->
                                    VideoPlayer(
                                        uri = media.url,
                                        previewUri = media.thumbnailUrl,
                                        contentDescription = media.description,
                                        modifier =
                                            Modifier
                                                .fillMaxSize(),
                                        onClick = {
                                            state.setShowUi(!state.showUi)
                                        },
                                        aspectRatio = media.aspectRatio,
                                        showControls = true,
                                        keepScreenOn = true,
                                        muted = false,
                                        onLongClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            state.setShowMenu(true)
                                        },
                                    )
                            }
                        }
                        if (pagerState.pageCount > 1) {
                            AnimatedVisibility(
                                visible = !state.lockPager,
                                enter = slideInVertically { it },
                                exit = slideOutVertically { it },
                                modifier =
                                    Modifier
                                        .wrapContentHeight()
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter),
                            ) {
                                Row(
                                    modifier =
                                        Modifier
                                            .padding(bottom = 8.dp)
                                            .systemBarsPadding(),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    repeat(pagerState.pageCount) { iteration ->
                                        val color =
                                            if (pagerState.currentPage == iteration) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                            }
                                        Box(
                                            modifier =
                                                Modifier
                                                    .padding(2.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                                    .size(8.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }.onLoading {
                        if (preview != null) {
                            AsyncImage(
                                model =
                                    ImageRequest.Builder(LocalContext.current)
                                        .data(preview)
                                        .memoryCacheKey(preview)
                                        .build(),
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .fillMaxSize(),
                            )
                            LinearProgressIndicator(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter),
                            )
                        } else {
                            Box(
                                modifier =
                                    Modifier
                                        .aspectRatio(1f)
                                        .fillMaxSize()
                                        .placeholder(true),
                            )
                        }
                    }
                }
            }
            state.status.onSuccess { status ->
                AnimatedVisibility(
                    visible = swiperState.progress == 0f && state.showUi,
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
                                .padding(
                                    bottom = if (state.withVideoPadding) 72.dp else 0.dp,
                                )
                                .systemBarsPadding(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                    )
                }
            }
            state.medias.onSuccess { medias ->
                if (state.showMenu) {
                    ModalBottomSheet(
                        onDismissRequest = {
                            state.setShowMenu(false)
                        },
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(text = stringResource(R.string.media_menu_save))
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Save,
                                    contentDescription = stringResource(R.string.media_menu_save),
                                )
                            },
                            modifier =
                                Modifier
                                    .clickable {
                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                            if (!permissionState.status.isGranted) {
                                                permissionState.launchPermissionRequest()
                                            } else {
                                                state.setShowMenu(false)
                                                val url =
                                                    when (val media = medias[state.currentPage]) {
                                                        is UiMedia.Audio -> media.url
                                                        is UiMedia.Gif -> media.url
                                                        is UiMedia.Image -> media.url
                                                        is UiMedia.Video -> media.url
                                                    }
                                                state.save(url)
                                            }
                                        } else {
                                            state.setShowMenu(false)
                                            val url =
                                                when (val media = medias[state.currentPage]) {
                                                    is UiMedia.Audio -> media.url
                                                    is UiMedia.Gif -> media.url
                                                    is UiMedia.Image -> media.url
                                                    is UiMedia.Video -> media.url
                                                }
                                            state.save(url)
                                        }
                                    },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun statusMediaPresenter(
    statusKey: MicroBlogKey,
    initialIndex: Int,
    context: Context,
    scope: CoroutineScope = koinInject(),
) = run {
    var showUi by remember {
        mutableStateOf(false)
    }
    var withVideoPadding by remember {
        mutableStateOf(false)
    }
    var showMenu by remember {
        mutableStateOf(false)
    }
    var lockPager by remember {
        mutableStateOf(false)
    }
    val state =
        remember(statusKey) {
            StatusPresenter(statusKey)
        }.invoke()
    val medias =
        state.status.map {
            it.medias
        }
    var currentPage by remember {
        mutableIntStateOf(initialIndex)
    }
    object : StatusState by state {
        val medias = medias
        val showUi = showUi
        val withVideoPadding = withVideoPadding
        val showMenu = showMenu
        val currentPage = currentPage
        val lockPager = lockPager

        fun setShowUi(value: Boolean) {
            if (!lockPager) {
                showUi = value
            }
        }

        fun setWithVideoPadding(value: Boolean) {
            withVideoPadding = value
        }

        fun setShowMenu(value: Boolean) {
            showMenu = value
        }

        fun setCurrentPage(value: Int) {
            currentPage = value
        }

        fun setLockPager(value: Boolean) {
            lockPager = value
            if (value) {
                showUi = false
            }
        }

        fun save(uri: String) {
            scope.launch {
                context.imageLoader.diskCache?.openSnapshot(uri)?.use {
                    val byteArray = it.data.toFile().readBytes()
                    val fileName = uri.substringAfterLast("/")
                    saveByteArrayToDownloads(context, byteArray, fileName)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.media_save_success),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }
}
