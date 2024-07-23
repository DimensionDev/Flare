package dev.dimension.flare.ui.screen.media

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import coil.request.ImageRequest
import com.eygraber.compose.placeholder.material3.placeholder
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.annotation.parameters.DeepLink
import com.ramcosta.composedestinations.annotation.parameters.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.generated.destinations.StatusRouteDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.VideoPlayer
import dev.dimension.flare.ui.component.VideoPlayerPool
import dev.dimension.flare.ui.component.status.QuotedStatus
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.StatusPresenter
import dev.dimension.flare.ui.presenter.status.StatusState
import dev.dimension.flare.ui.screen.home.NavigationState
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import okhttp3.internal.toImmutableList
import org.koin.compose.koinInject

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
@Destination<RootGraph>(
//    style = FullScreenDialogStyle::class,
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
    ],
)
internal fun AnimatedVisibilityScope.StatusMediaRoute(
    statusKey: MicroBlogKey,
    index: Int,
    preview: String?,
    navigator: DestinationsNavigator,
    accountType: AccountType,
    sharedTransitionScope: SharedTransitionScope,
    navigationState: NavigationState,
) = with(sharedTransitionScope) {
//    SetDialogDestinationToEdgeToEdge()
//    AnimatedVisibility(true) {
//        SharedTransitionScope {
    DisposableEffect(Unit) {
        navigationState.hide()
        navigationState.disableDrawer()
        onDispose {
            navigationState.show()
            navigationState.enableDrawer()
        }
    }
    StatusMediaScreen(
        statusKey = statusKey,
        accountType = accountType,
        index = index,
        preview = preview,
        toStatus = {
            navigator.navigate(StatusRouteDestination(statusKey, accountType))
        },
    )
//        }
//    }
}

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalPermissionsApi::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
private fun StatusMediaScreen(
    statusKey: MicroBlogKey,
    accountType: AccountType,
    index: Int,
    preview: String?,
    toStatus: () -> Unit,
    playerPool: VideoPlayerPool = koinInject(),
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val permissionState =
        rememberPermissionState(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
    val state by producePresenter {
        statusMediaPresenter(
            statusKey = statusKey,
            initialIndex = index,
            context = context,
            accountType = accountType,
        )
    }

    BackHandler(state.showUi) {
        state.setShowUi(false)
    }
    FlareTheme(darkTheme = true) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize(),
            ) {
                val pagerState =
                    rememberPagerState(
                        initialPage = index,
                        pageCount = {
                            when (val medias = state.medias) {
                                is UiState.Error -> 1
                                is UiState.Loading -> 1
                                is UiState.Success -> medias.data.size
                            }
                        },
                    )
                state.medias.onSuccess { media ->
                    LaunchedEffect(pagerState.currentPage) {
                        state.setWithVideoPadding(media[pagerState.currentPage] is UiMedia.Video)
                        state.setCurrentPage(pagerState.currentPage)
                    }
                    DisposableEffect(pagerState.currentPage) {
                        val player = playerPool.peek(media[pagerState.currentPage].url)
                        player?.play()
                        onDispose {
                            player?.pause()
                        }
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !state.lockPager,
                    key = {
                        when (val medias = state.medias) {
                            is UiState.Error -> preview
                            is UiState.Loading -> preview
                            is UiState.Success -> {
                                when (val item = medias.data[it]) {
                                    is UiMedia.Audio -> item.previewUrl
                                    is UiMedia.Gif -> item.previewUrl
                                    is UiMedia.Image -> item.previewUrl
                                    is UiMedia.Video -> item.thumbnailUrl
                                }
                            }
                        } ?: it
                    },
                ) { index ->
                    state.medias
                        .onSuccess { medias ->
                            when (val media = medias[index]) {
                                is UiMedia.Audio ->
                                    VideoPlayer(
                                        uri = media.url,
                                        previewUri = null,
                                        contentDescription = media.description,
                                        autoPlay = false,
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
                                        autoPlay = false,
                                        modifier =
                                            Modifier
//                                                .sharedElement(
//                                                    rememberSharedContentState(media.previewUrl),
//                                                    animatedVisibilityScope = this@AnimatedVisibilityScope,
//                                                )
                                                .fillMaxSize(),
                                        onClick = {
                                            state.setShowUi(!state.showUi)
                                        },
//                                                aspectRatio = media.aspectRatio,
                                        onLongClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            state.setShowMenu(true)
                                        },
                                        contentScale = ContentScale.Fit,
                                    )

                                is UiMedia.Image -> {
                                    ImageItem(
                                        modifier =
                                            Modifier
//                                            .sharedElement(
//                                                rememberSharedContentState(media.previewUrl),
//                                                animatedVisibilityScope = this@AnimatedVisibilityScope,
//                                            )
                                                .fillMaxSize(),
                                        url = media.url,
                                        previewUrl = media.previewUrl,
                                        description = media.description,
                                        onClick = {
                                            state.setShowUi(!state.showUi)
                                        },
                                        onLongClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            state.setShowMenu(true)
                                        },
                                        setLockPager = state::setLockPager,
                                    )
                                }

                                is UiMedia.Video ->
                                    VideoPlayer(
                                        uri = media.url,
                                        previewUri = media.thumbnailUrl,
                                        contentDescription = media.description,
                                        autoPlay = false,
                                        modifier =
                                            Modifier
                                                .fillMaxSize(),
                                        onClick = {
                                            state.setShowUi(!state.showUi)
                                        },
//                                                aspectRatio = media.aspectRatio,
                                        showControls = true,
                                        keepScreenOn = true,
                                        muted = false,
                                        onLongClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            state.setShowMenu(true)
                                        },
                                        contentScale = ContentScale.Fit,
                                    )
                            }
                        }.onLoading {
                            if (preview != null) {
                                ImageItem(
                                    url = preview,
                                    previewUrl = preview,
                                    description = null,
                                    onClick = { /*TODO*/ },
                                    onLongClick = { /*TODO*/ },
                                    setLockPager = state::setLockPager,
                                    modifier =
                                        Modifier
                                            .fillMaxSize(),
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
            }
            state.status.onSuccess { status ->
                val content = status.content
                if (content is UiTimeline.ItemContent.Status) {
                    AnimatedVisibility(
                        visible = state.showUi,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter),
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                    ) {
                        Card(
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                ),
                        ) {
                            QuotedStatus(
                                data = content,
                                modifier =
                                    Modifier
                                        .padding(
                                            bottom = if (state.withVideoPadding) 72.dp else 0.dp,
                                        ).systemBarsPadding(),
                            )
                        }
                    }
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
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
private fun ImageItem(
    url: String,
    previewUrl: String,
    description: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    setLockPager: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoomableState =
        rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 10f))
    LaunchedEffect(zoomableState.zoomFraction) {
        zoomableState.zoomFraction?.let {
            setLockPager(it > 0.01f)
        } ?: setLockPager(false)
    }
    val aspectRatio =
        remember(zoomableState.transformedContentBounds) {
            zoomableState.transformedContentBounds.let {
                it.height / it.width
            }
        }

    val alignment =
        remember(aspectRatio) {
            val targetAspectRatio = 19.5 / 9
            if (aspectRatio > targetAspectRatio) {
                Alignment.TopCenter
            } else {
                Alignment.Center
            }
        }

    val contentScale =
        remember(aspectRatio) {
            val targetAspectRatio = 19.5 / 9
            if (aspectRatio > targetAspectRatio) {
                ContentScale.FillWidth
            } else {
                ContentScale.Fit
            }
        }

    ZoomableAsyncImage(
        model =
            ImageRequest
                .Builder(LocalContext.current)
                .data(url)
                .placeholderMemoryCacheKey(previewUrl)
                .crossfade(1_000)
                .build(),
        contentDescription = description,
        state = rememberZoomableImageState(zoomableState),
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment,
        onClick = {
            onClick.invoke()
        },
        onLongClick = {
            onLongClick.invoke()
        },
    )
}

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun statusMediaPresenter(
    statusKey: MicroBlogKey,
    initialIndex: Int,
    context: Context,
    accountType: AccountType,
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
            StatusPresenter(accountType = accountType, statusKey = statusKey)
        }.invoke()
    val medias =
        state.status.map {
            (it.content as? UiTimeline.ItemContent.Status)?.images.orEmpty().toImmutableList()
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
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.media_save_success),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }
    }
}
