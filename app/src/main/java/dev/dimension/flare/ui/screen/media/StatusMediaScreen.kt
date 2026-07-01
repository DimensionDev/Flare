package dev.dimension.flare.ui.screen.media

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.window.core.layout.WindowSizeClass
import coil3.annotation.ExperimentalCoilApi
import coil3.imageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Backward
import compose.icons.fontawesomeicons.solid.CircleInfo
import compose.icons.fontawesomeicons.solid.Compress
import compose.icons.fontawesomeicons.solid.Copy
import compose.icons.fontawesomeicons.solid.Download
import compose.icons.fontawesomeicons.solid.Expand
import compose.icons.fontawesomeicons.solid.Forward
import compose.icons.fontawesomeicons.solid.GaugeHigh
import compose.icons.fontawesomeicons.solid.Pause
import compose.icons.fontawesomeicons.solid.Play
import compose.icons.fontawesomeicons.solid.ShareNodes
import compose.icons.fontawesomeicons.solid.Xmark
import dev.dimension.flare.R
import dev.dimension.flare.common.AndroidDownloadManager
import dev.dimension.flare.common.MediaFileNamePolicy
import dev.dimension.flare.common.shareImageMedia
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.Glassify
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.SurfaceBindingManager
import dev.dimension.flare.ui.component.VideoPlayer
import dev.dimension.flare.ui.component.placeholder
import dev.dimension.flare.ui.component.status.CommonStatusComponent
import dev.dimension.flare.ui.humanizer.humanize
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.StatusPresenter
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.Viewport
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.spatial.CoordinateSpace
import moe.tlaster.precompose.molecule.producePresenter
import moe.tlaster.swiper.Swiper
import moe.tlaster.swiper.rememberSwiperState
import org.koin.compose.koinInject
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalPermissionsApi::class,
)
@Composable
internal fun StatusMediaScreen(
    statusKey: MicroBlogKey,
    accountType: AccountType,
    index: Int,
    preview: String?,
    onDismiss: () -> Unit,
    toAltText: (UiMedia) -> Unit,
    uriHandler: UriHandler,
    surfaceBindingManager: SurfaceBindingManager = koinInject(),
) {
    val state by producePresenter {
        statusMediaPresenter(
            statusKey = statusKey,
            accountType = accountType,
        )
    }
    val status = state.status.takeSuccess() as? UiTimelineV2.Post
    MediaViewerScreen(
        medias = state.medias,
        initialIndex = index,
        preview = preview,
        onDismiss = onDismiss,
        toAltText = toAltText,
        uriHandler = uriHandler,
        fileName = { media ->
            MediaFileNamePolicy.statusMediaFileName(
                statusKey = statusKey.toString(),
                userHandle = status?.user?.handle?.canonical ?: "unknown",
                media = media,
            )
        },
        fileNames = { medias ->
            MediaFileNamePolicy.statusMediaFileNames(
                statusKey = statusKey.toString(),
                userHandle = status?.user?.handle?.canonical ?: "unknown",
                medias = medias,
            )
        },
        status = status,
        surfaceBindingManager = surfaceBindingManager,
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalPermissionsApi::class,
)
@Composable
internal fun MediaViewerScreen(
    medias: UiState<ImmutableList<UiMedia>>,
    initialIndex: Int,
    preview: String?,
    onDismiss: () -> Unit,
    toAltText: (UiMedia) -> Unit,
    uriHandler: UriHandler,
    fileName: (UiMedia) -> String,
    fileNames: (List<UiMedia>) -> Map<String, UiMedia>,
    status: UiTimelineV2.Post? = null,
    surfaceBindingManager: SurfaceBindingManager = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val isBigScreen = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND)
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val permissionState =
        rememberPermissionState(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
    val state =
        mediaViewerPresenter(
            medias = medias,
            initialIndex = initialIndex,
            context = context,
            fileName = fileName,
            fileNames = fileNames,
        )
    val pagerState =
        rememberPagerState(
            initialPage = initialIndex,
            pageCount = {
                when (val medias = state.medias) {
                    is UiState.Error -> 1
                    is UiState.Loading -> 1
                    is UiState.Success -> medias.data.size.coerceAtLeast(1)
                }
            },
        )
    var playbackSpeed by remember { mutableFloatStateOf(NORMAL_PLAYBACK_SPEED) }
    MediaLandscapeEffect(
        enabled = state.isLandscapeViewing,
        originalOrientation = state.originalOrientation,
        setOriginalOrientation = state::setOriginalOrientation,
    )
    LaunchedEffect(pagerState.currentPage) {
        state.setCurrentPage(pagerState.currentPage)
        playbackSpeed = NORMAL_PLAYBACK_SPEED
        surfaceBindingManager.player.setPlaybackSpeed(NORMAL_PLAYBACK_SPEED)
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
            Box(
                modifier =
                    Modifier
                        .fillMaxSize(),
            ) {
                Row {
                    Box(
                        modifier = Modifier.weight(1f),
                    ) {
                        Swiper(
                            state = swiperState,
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                userScrollEnabled = !state.lockPager,
                                key = {
                                    when (val medias = state.medias) {
                                        is UiState.Error -> {
                                            preview
                                        }

                                        is UiState.Loading -> {
                                            preview
                                        }

                                        is UiState.Success -> {
                                            medias.data.getOrNull(it)?.previewKey()
                                        }
                                    } ?: it
                                },
                            ) { index ->
                                AnimatedContent(
                                    state.medias,
                                    transitionSpec = {
                                        fadeIn() togetherWith fadeOut()
                                    },
                                ) {
                                    it
                                        .onSuccess { medias ->
                                            val media = medias.getOrNull(index)
                                            if (media == null) {
                                                Box(
                                                    modifier =
                                                        Modifier
                                                            .aspectRatio(1f)
                                                            .fillMaxSize()
                                                            .placeholder(true),
                                                )
                                                return@onSuccess
                                            }
                                            val imageUrl =
                                                when (media) {
                                                    is UiMedia.Audio -> media.previewUrl ?: media.url
                                                    is UiMedia.Gif -> media.url
                                                    is UiMedia.Image -> media.url
                                                    is UiMedia.Video -> media.thumbnailUrl
                                                }
                                            val previewUrl =
                                                when (media) {
                                                    is UiMedia.Audio -> media.previewUrl ?: media.url
                                                    is UiMedia.Gif -> media.previewUrl
                                                    is UiMedia.Image -> media.previewUrl
                                                    is UiMedia.Video -> media.thumbnailUrl
                                                }
                                            if (pagerState.currentPage != index || media is UiMedia.Image || media is UiMedia.Gif) {
                                                ImageItem(
                                                    modifier =
                                                        Modifier
                                                            .fillMaxSize(),
                                                    url = imageUrl,
                                                    previewUrl = previewUrl,
                                                    customHeaders = media.customHeaders,
                                                    description = media.description,
                                                    onClick = {
                                                        state.setShowUi(!state.showUi)
                                                    },
                                                    setLockPager = {
                                                        if (pagerState.currentPage == index) {
                                                            if (!isBigScreen) {
                                                                state.setShowUi(!it)
                                                            }
                                                            state.setLockPager(it)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        hapticFeedback.performHapticFeedback(
                                                            HapticFeedbackType.LongPress,
                                                        )
                                                        state.setShowSheet(true)
                                                    },
                                                )
                                            } else if (media is UiMedia.Video) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                ) {
                                                    VideoPlayer(
                                                        uri = media.url,
                                                        customHeaders = media.customHeaders,
                                                        previewUri = media.thumbnailUrl,
                                                        contentDescription = media.description,
                                                        modifier = Modifier.fillMaxSize(),
                                                        aspectRatio = media.aspectRatio,
                                                        autoPlay = true,
                                                        onClick = null,
                                                        showControls = true,
                                                        keepScreenOn = true,
                                                        muted = false,
                                                        contentScale = ContentScale.Fit,
                                                    )
                                                    VideoGestureOverlay(
                                                        player = surfaceBindingManager.player,
                                                        onClick = {
                                                            state.setShowUi(!state.showUi)
                                                        },
                                                        onPlaybackSpeedChanged = {
                                                            playbackSpeed = it
                                                        },
                                                        modifier = Modifier.fillMaxSize(),
                                                    )
                                                }
                                            } else if (media is UiMedia.Audio) {
                                                VideoPlayer(
                                                    uri = media.url,
                                                    customHeaders = media.customHeaders,
                                                    previewUri = null,
                                                    contentDescription = media.description,
                                                    autoPlay = false,
                                                    onClick = {
                                                        state.setShowUi(!state.showUi)
                                                    },
                                                    onLongClick = {
                                                        hapticFeedback.performHapticFeedback(
                                                            HapticFeedbackType.LongPress,
                                                        )
                                                        state.setShowSheet(true)
                                                    },
                                                )
                                            }
                                        }.onLoading {
                                            if (preview != null) {
                                                ImageItem(
                                                    url = preview,
                                                    previewUrl = preview,
                                                    customHeaders = null,
                                                    description = null,
                                                    onClick = { /*TODO*/ },
                                                    setLockPager = {
                                                        if (!isBigScreen) {
                                                            state.setShowUi(!it)
                                                        }
                                                        state.setLockPager(it)
                                                    },
                                                    modifier =
                                                        Modifier
                                                            .fillMaxSize(),
                                                    onLongClick = { },
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
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = state.showUi,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter),
                            enter = slideInVertically { -it },
                            exit = slideOutVertically { -it },
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .systemBarsPadding()
                                        .padding(horizontal = 4.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Glassify(
                                    onClick = {
                                        onDismiss.invoke()
                                    },
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
//                            colors = IconButtonDefaults.filledTonalIconButtonColors(
//                                containerColor = Color.Transparent,
//                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
//                            )
                                ) {
                                    FAIcon(
                                        FontAwesomeIcons.Solid.Xmark,
                                        contentDescription = stringResource(id = R.string.navigate_back),
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                state.medias.onSuccess { medias ->
                                    val current = medias.getOrNull(state.currentPage) ?: return@onSuccess
                                    Glassify(
                                        onClick = {
                                            state.setLandscapeViewing(!state.isLandscapeViewing)
                                        },
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.size(40.dp),
                                        shape = CircleShape,
                                    ) {
                                        FAIcon(
                                            if (state.isLandscapeViewing) {
                                                FontAwesomeIcons.Solid.Compress
                                            } else {
                                                FontAwesomeIcons.Solid.Expand
                                            },
                                            contentDescription = if (state.isLandscapeViewing) "Exit landscape view" else "Landscape view",
                                        )
                                    }
                                    if (!current.description.isNullOrEmpty()) {
                                        Glassify(
                                            onClick = {
                                                toAltText.invoke(current)
                                            },
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier.size(40.dp),
                                            shape = CircleShape,
                                        ) {
                                            FAIcon(
                                                FontAwesomeIcons.Solid.CircleInfo,
                                                contentDescription = stringResource(id = R.string.media_alt_text),
                                            )
                                        }
                                    }
                                    Glassify(
                                        onClick = {
                                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                                if (!permissionState.status.isGranted) {
                                                    permissionState.launchPermissionRequest()
                                                } else {
                                                    state.save(current)
                                                }
                                            } else {
                                                state.save(current)
                                            }
                                        },
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.size(40.dp),
                                        shape = CircleShape,
                                    ) {
                                        FAIcon(
                                            FontAwesomeIcons.Solid.Download,
                                            contentDescription = stringResource(id = R.string.media_menu_save),
                                        )
                                    }
                                    AnimatedVisibility(current is UiMedia.Image) {
                                        Glassify(
                                            onClick = {
                                                state.shareMedia(current)
                                            },
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier.size(40.dp),
                                            shape = CircleShape,
                                        ) {
                                            FAIcon(
                                                FontAwesomeIcons.Solid.ShareNodes,
                                                contentDescription = stringResource(id = R.string.media_menu_share_image),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val currentMedia = state.medias.takeSuccess()?.getOrNull(state.currentPage)
                        val isCurrentVideo = currentMedia is UiMedia.Video
                        val shouldShowBottomUi =
                            when {
                                state.isLandscapeViewing -> {
                                    isCurrentVideo &&
                                        (state.showUi || playbackSpeed > NORMAL_PLAYBACK_SPEED)
                                }

                                isCurrentVideo -> {
                                    state.showUi || playbackSpeed > NORMAL_PLAYBACK_SPEED
                                }

                                else -> {
                                    state.showUi &&
                                        (pagerState.pageCount > 1 || status != null)
                                }
                            }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = shouldShowBottomUi,
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter),
                            enter = slideInVertically { it },
                            exit = slideOutVertically { it },
                        ) {
                            Glassify(
                                modifier =
                                    Modifier
                                        .let {
                                            if (isBigScreen) {
                                                it
                                                    .safeContentPadding()
                                                    .clip(
                                                        MaterialTheme.shapes.medium,
                                                    )
                                            } else {
                                                it
                                                    .fillMaxWidth()
                                            }
                                        },
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.onBackground,
                            ) {
                                Column(
                                    modifier =
                                        Modifier.let {
                                            if (status == null && !isBigScreen) {
                                                it.windowInsetsPadding(
                                                    WindowInsets.systemBars.only(
                                                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                                    ),
                                                )
                                            } else {
                                                it
                                            }
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    if (state.showUi && !state.isLandscapeViewing && pagerState.pageCount > 1) {
                                        if (status == null && pagerState.pageCount > 10) {
                                            MediaPageSlider(
                                                pageCount = pagerState.pageCount,
                                                currentPage = pagerState.currentPage,
                                                onPageSelected = { page ->
                                                    scope.launch {
                                                        if (pagerState.currentPage != page) {
                                                            pagerState.scrollToPage(page)
                                                        }
                                                    }
                                                },
                                                modifier =
                                                    Modifier
                                                        .let {
                                                            if (isBigScreen) {
                                                                it
                                                            } else {
                                                                it.padding(
                                                                    start = 16.dp,
                                                                    top = 8.dp,
                                                                    end = 16.dp,
                                                                )
                                                            }
                                                        }.widthIn(max = 480.dp),
                                            )
                                        } else {
                                            Row(
                                                modifier =
                                                    Modifier.let {
                                                        if (isBigScreen) {
                                                            it
                                                        } else {
                                                            it.padding(top = 8.dp)
                                                        }
                                                    },
                                                horizontalArrangement = Arrangement.Center,
                                            ) {
                                                repeat(pagerState.pageCount) { iteration ->
                                                    val color =
                                                        if (pagerState.currentPage == iteration) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.onBackground.copy(
                                                                alpha = 0.5f,
                                                            )
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
                                    state.medias.onSuccess { medias ->
                                        val current =
                                            remember(
                                                medias,
                                                state.currentPage,
                                            ) {
                                                medias.getOrNull(state.currentPage)
                                            }
                                        if (current is UiMedia.Video) {
                                            PlayerControl(
                                                surfaceBindingManager.player,
                                                playbackSpeed = playbackSpeed,
                                                modifier =
                                                    Modifier
                                                        .widthIn(max = 480.dp),
                                            )
                                        }
                                    }
                                    if (status != null && !isBigScreen && state.showUi && !state.isLandscapeViewing) {
                                        CompositionLocalProvider(
                                            LocalTimelineAppearance provides
                                                LocalTimelineAppearance.current.copy(
                                                    showMedia = false,
                                                    showLinkPreview = false,
                                                ),
                                            LocalUriHandler provides uriHandler,
                                        ) {
                                            CommonStatusComponent(
                                                item = status,
                                                showMedia = false,
                                                modifier =
                                                    Modifier
                                                        .padding(
                                                            horizontal = screenHorizontalPadding,
                                                            vertical = 8.dp,
                                                        ).windowInsetsPadding(
                                                            WindowInsets.systemBars.only(
                                                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                                            ),
                                                        ),
                                                maxLines = 3,
                                                showExpandButton = false,
                                                isQuote = true,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (isBigScreen && status != null) {
                        AnimatedVisibility(state.showUi && !state.isLandscapeViewing) {
                            Surface(
                                modifier =
                                    Modifier
                                        .width(320.dp)
                                        .fillMaxHeight()
                                        .verticalScroll(rememberScrollState()),
                                color = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ) {
                                CompositionLocalProvider(
                                    LocalTimelineAppearance provides
                                        LocalTimelineAppearance.current.copy(
                                            showMedia = false,
                                            showLinkPreview = false,
                                        ),
                                    LocalUriHandler provides uriHandler,
                                ) {
                                    CommonStatusComponent(
                                        item = status,
                                        showMedia = false,
                                        modifier =
                                            Modifier
                                                .padding(
                                                    horizontal = screenHorizontalPadding,
                                                    vertical = 8.dp,
                                                ).windowInsetsPadding(
                                                    WindowInsets.systemBars.only(
                                                        WindowInsetsSides.End + WindowInsetsSides.Vertical,
                                                    ),
                                                ),
                                        maxLines = Int.MAX_VALUE,
                                        showExpandButton = false,
                                        isQuote = false,
                                        isDetail = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (state.showSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    state.setShowSheet(false)
                },
            ) {
                ListItem(
                    headlineContent = {
                        Text(stringResource(id = R.string.media_menu_save))
                    },
                    leadingContent = {
                        FAIcon(
                            FontAwesomeIcons.Solid.Download,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    modifier =
                        Modifier
                            .clickable {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                    if (!permissionState.status.isGranted) {
                                        permissionState.launchPermissionRequest()
                                    } else {
                                        state.medias.onSuccess { medias ->
                                            medias.getOrNull(state.currentPage)?.let(state::save)
                                        }
                                    }
                                } else {
                                    state.medias.onSuccess { medias ->
                                        medias.getOrNull(state.currentPage)?.let(state::save)
                                    }
                                }
                                state.setShowSheet(false)
                            },
                    colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                )
                state.medias.onSuccess { medias ->
                    if (medias.size > 1) {
                        ListItem(
                            headlineContent = {
                                Text(stringResource(id = R.string.media_menu_download_all))
                            },
                            leadingContent = {
                                FAIcon(
                                    FontAwesomeIcons.Solid.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            modifier =
                                Modifier
                                    .clickable {
                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                            if (!permissionState.status.isGranted) {
                                                permissionState.launchPermissionRequest()
                                            } else {
                                                state.saveAll(medias)
                                            }
                                        } else {
                                            state.saveAll(medias)
                                        }
                                        state.setShowSheet(false)
                                    },
                            colors =
                                ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                ),
                        )
                    }

                    val current = medias.getOrNull(state.currentPage)
                    if (current is UiMedia.Image) {
                        ListItem(
                            headlineContent = {
                                Text(stringResource(id = R.string.media_menu_share_image))
                            },
                            leadingContent = {
                                FAIcon(
                                    FontAwesomeIcons.Solid.ShareNodes,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            modifier =
                                Modifier
                                    .clickable {
                                        state.shareMedia(current)
                                        state.setShowSheet(false)
                                    },
                            colors =
                                ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                ),
                        )
                    }
                }

                state.medias.onSuccess { medias ->
                    val label = stringResource(R.string.media_menu_media_link)
                    val current = medias.getOrNull(state.currentPage) ?: return@onSuccess
                    ListItem(
                        headlineContent = {
                            Text(stringResource(id = R.string.media_menu_copy_link))
                        },
                        leadingContent = {
                            FAIcon(
                                FontAwesomeIcons.Solid.Copy,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        modifier =
                            Modifier
                                .clickable {
                                    scope.launch {
                                        val url = current.url
                                        clipboard.setClipEntry(
                                            ClipEntry(
                                                ClipData.newRawUri(
                                                    label,
                                                    url.toUri(),
                                                ),
                                            ),
                                        )
                                        state.setShowSheet(false)
                                    }
                                },
                        colors =
                            ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaPageSlider(
    pageCount: Int,
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxPage = (pageCount - 1).coerceAtLeast(0)
    var isDragging by remember { mutableStateOf(false) }
    var sliderValue by remember(pageCount) {
        mutableFloatStateOf(currentPage.coerceIn(0, maxPage).toFloat())
    }
    LaunchedEffect(currentPage, maxPage, isDragging) {
        if (!isDragging) {
            sliderValue = currentPage.coerceIn(0, maxPage).toFloat()
        }
    }

    val sliderPage = sliderValue.roundToInt().coerceIn(0, maxPage)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = (sliderPage + 1).toString(),
            style = MaterialTheme.typography.labelMedium,
        )
        Slider(
            value = sliderValue.coerceIn(0f, maxPage.toFloat()),
            onValueChange = { value ->
                val page = value.roundToInt().coerceIn(0, maxPage)
                isDragging = true
                sliderValue = page.toFloat()
                if (page != currentPage) {
                    onPageSelected(page)
                }
            },
            onValueChangeFinished = {
                val page = sliderValue.roundToInt().coerceIn(0, maxPage)
                isDragging = false
                if (page != currentPage) {
                    onPageSelected(page)
                }
            },
            valueRange = 0f..maxPage.toFloat(),
            steps = (pageCount - 2).coerceAtLeast(0),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = pageCount.toString(),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerControl(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
    playbackSpeed: Float = NORMAL_PLAYBACK_SPEED,
) {
    val playPauseButtonState = rememberPlayPauseButtonState(player)
    var isLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(player) {
        while (!isLoaded) {
            isLoaded = player.isPlaying
            awaitFrame()
        }
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            visible = playbackSpeed > NORMAL_PLAYBACK_SPEED,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                modifier =
                    Modifier
                        .padding(top = 8.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FAIcon(
                    FontAwesomeIcons.Solid.GaugeHigh,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White,
                )
                Text(
                    "${playbackSpeed.formatSpeed()}x",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!isLoaded) {
                LinearWavyProgressIndicator(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                )
            } else {
                var time by remember { mutableStateOf("") }
                var isSliderChanging by remember {
                    mutableStateOf(false)
                }
                var sliderValue by remember {
                    mutableFloatStateOf(0f)
                }
                if (!playPauseButtonState.showPlay && !isSliderChanging) {
                    LaunchedEffect(Unit) {
                        while (true) {
                            sliderValue = player.currentPosition.toFloat() / player.duration.toFloat()
                            time =
                                buildString {
                                    append(player.currentPosition.milliseconds.humanize())
                                    append(" / ")
                                    append(player.duration.milliseconds.humanize())
                                }
                            awaitFrame()
                        }
                    }
                }
                IconButton(
                    onClick = {
                        playPauseButtonState.onClick()
                    },
                    enabled = playPauseButtonState.isEnabled,
                ) {
                    Icon(
                        if (playPauseButtonState.showPlay) {
                            FontAwesomeIcons.Solid.Play
                        } else {
                            FontAwesomeIcons.Solid.Pause
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Slider(
                    value = sliderValue,
                    onValueChange = {
                        isSliderChanging = true
                        sliderValue = it
                        time =
                            buildString {
                                append((player.duration * it).toLong().milliseconds.humanize())
                                append(" / ")
                                append(player.duration.milliseconds.humanize())
                            }
                    },
                    onValueChangeFinished = {
                        player.seekTo((player.duration * sliderValue).toLong())
                        isSliderChanging = false
                    },
                    modifier = Modifier.weight(1f),
                )
                Text(time)
                Spacer(Modifier.width(screenHorizontalPadding))
            }
        }
    }
}

private fun Float.formatSpeed(): String =
    if (this % 1f == 0f) {
        toInt().toString()
    } else {
        "%.1f".format(this)
    }

@Composable
private fun VideoGestureOverlay(
    player: ExoPlayer,
    onClick: () -> Unit,
    onPlaybackSpeedChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnPlaybackSpeedChanged by rememberUpdatedState(onPlaybackSpeedChanged)
    var seekFeedback by remember { mutableStateOf<SeekFeedback?>(null) }
    var seekFeedbackVersion by remember { mutableIntStateOf(0) }
    var fastPlaybackWasPlaying by remember { mutableStateOf(false) }
    var isFastPlayback by remember { mutableStateOf(false) }

    fun showSeekFeedback(feedback: SeekFeedback) {
        seekFeedback = feedback
        seekFeedbackVersion += 1
    }

    fun beginFastPlayback() {
        if (isFastPlayback) return
        fastPlaybackWasPlaying = player.isPlaying
        player.setPlaybackSpeed(FAST_PLAYBACK_SPEED)
        if (!player.isPlaying) {
            player.play()
        }
        isFastPlayback = true
        currentOnPlaybackSpeedChanged(FAST_PLAYBACK_SPEED)
    }

    fun endFastPlayback() {
        if (!isFastPlayback) return
        player.setPlaybackSpeed(NORMAL_PLAYBACK_SPEED)
        if (!fastPlaybackWasPlaying) {
            player.pause()
        }
        isFastPlayback = false
        currentOnPlaybackSpeedChanged(NORMAL_PLAYBACK_SPEED)
    }

    LaunchedEffect(seekFeedbackVersion) {
        if (seekFeedbackVersion > 0) {
            delay(SEEK_FEEDBACK_VISIBLE_MS)
            seekFeedback = null
        }
    }

    androidx.compose.runtime.DisposableEffect(player) {
        onDispose {
            if (isFastPlayback) {
                player.setPlaybackSpeed(NORMAL_PLAYBACK_SPEED)
                currentOnPlaybackSpeedChanged(NORMAL_PLAYBACK_SPEED)
            }
        }
    }

    Box(
        modifier =
            modifier
                .pointerInput(player) {
                    detectTapGestures(
                        onTap = {
                            currentOnClick()
                        },
                        onDoubleTap = { offset ->
                            if (offset.x < size.width / 2f) {
                                player.seekBy(-SEEK_INTERVAL_MS)
                                showSeekFeedback(SeekFeedback.Backward)
                            } else {
                                player.seekBy(SEEK_INTERVAL_MS)
                                showSeekFeedback(SeekFeedback.Forward)
                            }
                        },
                        onLongPress = {},
                        onPress = {
                            var longPressStarted = false
                            coroutineScope {
                                val longPressJob =
                                    launch {
                                        delay(LONG_PRESS_DELAY_MS)
                                        longPressStarted = true
                                        beginFastPlayback()
                                    }
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    longPressJob.cancel()
                                    if (longPressStarted) {
                                        endFastPlayback()
                                    }
                                }
                            }
                        },
                    )
                },
    ) {
        SeekFeedbackOverlay(seekFeedback)
    }
}

@Composable
private fun SeekFeedbackOverlay(feedback: SeekFeedback?) {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        AnimatedVisibility(
            visible = feedback == SeekFeedback.Backward,
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 56.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            SeekFeedbackContent(SeekFeedback.Backward)
        }
        AnimatedVisibility(
            visible = feedback == SeekFeedback.Forward,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 56.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            SeekFeedbackContent(SeekFeedback.Forward)
        }
    }
}

@Composable
private fun SeekFeedbackContent(feedback: SeekFeedback) {
    Column(
        modifier =
            Modifier
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FAIcon(
            when (feedback) {
                SeekFeedback.Backward -> FontAwesomeIcons.Solid.Backward
                SeekFeedback.Forward -> FontAwesomeIcons.Solid.Forward
            },
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = Color.White,
        )
        Text(
            "5s",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private enum class SeekFeedback {
    Backward,
    Forward,
}

private fun ExoPlayer.seekBy(offsetMs: Long) {
    val durationMs = duration.takeIf { it > 0L && it != C.TIME_UNSET }
    val targetMs = currentPosition + offsetMs
    seekTo(
        if (durationMs != null) {
            targetMs.coerceIn(0L, durationMs)
        } else {
            targetMs.coerceAtLeast(0L)
        },
    )
}

private const val SEEK_INTERVAL_MS = 5_000L
private const val LONG_PRESS_DELAY_MS = 350L
private const val SEEK_FEEDBACK_VISIBLE_MS = 450L
private const val NORMAL_PLAYBACK_SPEED = 1f
private const val FAST_PLAYBACK_SPEED = 2f

@OptIn(ExperimentalTelephotoApi::class)
@Composable
private fun ImageItem(
    url: String,
    previewUrl: String,
    customHeaders: ImmutableMap<String, String>?,
    description: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    setLockPager: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentSetLockPager by rememberUpdatedState(setLockPager)
    val scope = rememberCoroutineScope()
    val zoomableState =
        rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 10f))
    LaunchedEffect(zoomableState.zoomFraction) {
        zoomableState.zoomFraction?.let {
            currentSetLockPager(it > 0.01f)
        } ?: currentSetLockPager(false)
    }
    BackHandler(
        enabled = (zoomableState.zoomFraction ?: 0f) > 0.01f,
    ) {
        scope.launch {
            zoomableState.resetZoom()
        }
    }
    var alignment by remember {
        mutableStateOf(Alignment.Center)
    }
    var contentScale by remember {
        mutableStateOf(ContentScale.Fit)
    }
    LaunchedEffect(zoomableState.coordinateSystem.contentBounds(false)) {
        // only set once to prevent jitter
        if (contentScale == ContentScale.Fit) {
            val aspectRatio =
                with(zoomableState.coordinateSystem) {
                    contentBounds(false).rectIn(CoordinateSpace.Viewport)
                }.let {
                    it.height / it.width
                }
            val targetAspectRatio = 19.5f / 9f
            if (aspectRatio > targetAspectRatio) {
                alignment = Alignment.TopCenter
                contentScale = ContentScale.FillWidth
            }
        }
    }

    ZoomableAsyncImage(
        model =
            ImageRequest
                .Builder(LocalContext.current)
                .data(url)
                .placeholderMemoryCacheKey(previewUrl)
                .crossfade(1_000)
                .size(Size.ORIGINAL)
                .let { builder ->
                    if (customHeaders.isNullOrEmpty()) {
                        builder
                    } else {
                        builder.httpHeaders(
                            NetworkHeaders
                                .Builder()
                                .apply {
                                    customHeaders.forEach { (key, value) ->
                                        set(key, value)
                                    }
                                }.build(),
                        )
                    }
                }.build(),
        contentDescription = description,
        state = rememberZoomableImageState(zoomableState),
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment,
        onClick = {
            currentOnClick()
        },
        onLongClick = {
            currentOnLongClick()
        },
        onDoubleClick = DoubleClickToZoomListener.cycle(2f),
    )
}

private fun UiMedia.previewKey(): String? =
    when (this) {
        is UiMedia.Audio -> previewUrl
        is UiMedia.Gif -> previewUrl
        is UiMedia.Image -> previewUrl
        is UiMedia.Video -> thumbnailUrl
    }

@Composable
private fun statusMediaPresenter(
    statusKey: MicroBlogKey,
    accountType: AccountType,
) = run {
    val state =
        remember(accountType, statusKey) {
            StatusPresenter(accountType = accountType, statusKey = statusKey)
        }.invoke()
    var medias: UiState<ImmutableList<UiMedia>> by remember(accountType, statusKey) {
        mutableStateOf(UiState.Loading())
    }
    // Prevent media changes after the first successful load so the visible page stays stable.
    if (!medias.isSuccess) {
        LaunchedEffect(state) {
            state.status
                .onSuccess {
                    medias =
                        UiState.Success(
                            (it as? UiTimelineV2.Post)
                                ?.images
                                .orEmpty()
                                .toImmutableList(),
                        )
                }
        }
    }
    object {
        val status = state.status
        val medias = medias
    }
}

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun mediaViewerPresenter(
    medias: UiState<ImmutableList<UiMedia>>,
    initialIndex: Int,
    context: Context,
    fileName: (UiMedia) -> String,
    fileNames: (List<UiMedia>) -> Map<String, UiMedia>,
    scope: CoroutineScope = koinInject(),
    mediaDownloadManager: AndroidDownloadManager = koinInject(),
) = run {
    var showSheet by remember {
        mutableStateOf(false)
    }
    var showUi by remember {
        mutableStateOf(true)
    }
    var lockPager by remember {
        mutableStateOf(false)
    }
    var isLandscapeViewing by remember {
        mutableStateOf(false)
    }
    var originalOrientation: Int? by remember {
        mutableStateOf<Int?>(null)
    }
    var currentPage by remember {
        mutableIntStateOf(initialIndex)
    }
    object {
        val medias = medias
        val showUi = showUi
        val currentPage = currentPage
        val lockPager = lockPager
        val showSheet = showSheet
        val isLandscapeViewing = isLandscapeViewing
        val originalOrientation = originalOrientation

        fun setShowSheet(value: Boolean) {
            showSheet = value
        }

        fun setShowUi(value: Boolean) {
            if (!lockPager) {
                showUi = value
            }
        }

        fun setCurrentPage(value: Int) {
            currentPage = value
        }

        fun setLockPager(value: Boolean) {
            lockPager = value
        }

        fun setLandscapeViewing(value: Boolean) {
            isLandscapeViewing = value
        }

        fun setOriginalOrientation(value: Int?) {
            originalOrientation = value
        }

        fun save(data: UiMedia) {
            val targetFileName = fileName(data)
            when (data) {
                is UiMedia.Audio -> download(data.url, targetFileName, data.customHeaders)
                is UiMedia.Gif -> download(data.url, targetFileName, data.customHeaders)
                is UiMedia.Image -> save(data.url, targetFileName)
                is UiMedia.Video -> download(data.url, targetFileName, data.customHeaders)
            }
        }

        fun saveAll(data: List<UiMedia>) {
            scope.launch {
                withContext(Dispatchers.Main) {
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.media_download_started),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
                val result =
                    runCatching {
                        mediaDownloadManager.downloadAllMedia(fileNames(data))
                    }.getOrNull()
                withContext(Dispatchers.Main) {
                    Toast
                        .makeText(
                            context,
                            context.getString(
                                if (result != null && result.failedFileNames.isEmpty()) {
                                    R.string.media_save_success
                                } else {
                                    R.string.media_save_fail
                                },
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }

        fun shareMedia(data: UiMedia) {
            when (data) {
                is UiMedia.Audio -> {}

                is UiMedia.Gif -> {}

                is UiMedia.Image -> {
                    scope.launch {
                        shareImageMedia(
                            context = context,
                            media = data,
                            fileName = fileName(data),
                        )
                    }
                }

                is UiMedia.Video -> {}
            }
        }

        fun download(
            uri: String,
            fileName: String,
            customHeaders: Map<String, String>?,
        ) {
            scope.launch {
                mediaDownloadManager.downloadMedia(
                    uri = uri,
                    fileName = fileName,
                    customHeaders = customHeaders,
                    callback =
                        object : AndroidDownloadManager.DownloadCallback {
                            override fun onDownloadStarted(downloadId: Long) {
                                scope.launch {
                                    withContext(Dispatchers.Main) {
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(R.string.media_download_started),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    }
                                }
                            }

                            override fun onDownloadSuccess(downloadId: Long) {
                                scope.launch {
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

                            override fun onDownloadFailed(downloadId: Long) {
                                scope.launch {
                                    withContext(Dispatchers.Main) {
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(R.string.media_save_fail),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    }
                                }
                            }
                        },
                )
            }
        }

        fun save(
            uri: String,
            fileName: String,
        ) {
            scope.launch {
                context.imageLoader.diskCache?.openSnapshot(uri)?.use {
                    val byteArray = it.data.toFile().readBytes()
                    val success =
                        mediaDownloadManager.saveByteArray(
                            byteArray = byteArray,
                            fileName = fileName,
                        )
                    withContext(Dispatchers.Main) {
                        Toast
                            .makeText(
                                context,
                                context.getString(
                                    if (success) {
                                        R.string.media_save_success
                                    } else {
                                        R.string.media_save_fail
                                    },
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                } ?: withContext(Dispatchers.Main) {
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.media_is_downloading),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }
    }
}
