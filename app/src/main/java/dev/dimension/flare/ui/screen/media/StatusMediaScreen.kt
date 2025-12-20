package dev.dimension.flare.ui.screen.media

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import coil3.annotation.ExperimentalCoilApi
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleInfo
import compose.icons.fontawesomeicons.solid.Download
import compose.icons.fontawesomeicons.solid.Pause
import compose.icons.fontawesomeicons.solid.Play
import compose.icons.fontawesomeicons.solid.ShareNodes
import compose.icons.fontawesomeicons.solid.Xmark
import dev.dimension.flare.R
import dev.dimension.flare.common.VideoDownloadHelper
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.Glassify
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.component.SurfaceBindingManager
import dev.dimension.flare.ui.component.VideoPlayer
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.component.status.CommonStatusComponent
import dev.dimension.flare.ui.humanizer.humanize
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.getFileName
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.StatusPresenter
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.fornewid.placeholder.material3.placeholder
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.awaitFrame
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
    val isBigScreen = isBigScreen()
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
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
    LaunchedEffect(pagerState.currentPage) {
        state.setCurrentPage(pagerState.currentPage)
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
                                AnimatedContent(
                                    state.medias,
                                    transitionSpec = {
                                        fadeIn() togetherWith fadeOut()
                                    },
                                ) {
                                    it
                                        .onSuccess { medias ->
                                            val media = medias[index]
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
                                                VideoPlayer(
                                                    uri = media.url,
                                                    previewUri = media.thumbnailUrl,
                                                    contentDescription = media.description,
                                                    aspectRatio = media.aspectRatio,
                                                    autoPlay = true,
                                                    onClick = {
                                                        state.setShowUi(!state.showUi)
                                                    },
                                                    showControls = true,
                                                    keepScreenOn = true,
                                                    muted = false,
                                                    contentScale = ContentScale.Fit,
                                                    onLongClick = {
                                                        hapticFeedback.performHapticFeedback(
                                                            HapticFeedbackType.LongPress,
                                                        )
                                                        state.setShowSheet(true)
                                                    },
                                                )
                                            } else if (media is UiMedia.Audio) {
                                                VideoPlayer(
                                                    uri = media.url,
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
                                    val current = medias[state.currentPage]
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

                        state.status.onSuccess { status ->
                            val content = status.content
                            if (content is UiTimeline.ItemContent.Status) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = state.showUi,
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
                                                        it.fillMaxWidth()
                                                    }
                                                },
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        contentColor = MaterialTheme.colorScheme.onBackground,
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            if (pagerState.pageCount > 1) {
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
                                            state.medias.onSuccess { medias ->
                                                val current =
                                                    remember(
                                                        medias,
                                                        state.currentPage,
                                                    ) {
                                                        medias[state.currentPage]
                                                    }
                                                if (current is UiMedia.Video) {
                                                    PlayerControl(
                                                        surfaceBindingManager.player,
                                                        modifier =
                                                            Modifier
                                                                .widthIn(max = 480.dp),
                                                    )
                                                }
                                            }
                                            if (!isBigScreen) {
                                                CompositionLocalProvider(
                                                    LocalComponentAppearance provides
                                                        LocalComponentAppearance.current.copy(
                                                            showMedia = false,
                                                            showLinkPreview = false,
                                                        ),
                                                    LocalUriHandler provides uriHandler,
                                                ) {
                                                    CommonStatusComponent(
                                                        item = content,
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
                        }
                    }
                    if (isBigScreen) {
                        AnimatedVisibility(state.showUi) {
                            Surface(
                                modifier =
                                    Modifier
                                        .width(320.dp)
                                        .fillMaxHeight()
                                        .verticalScroll(rememberScrollState()),
                                color = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ) {
                                state.status.onSuccess {
                                    val content = it.content
                                    if (content is UiTimeline.ItemContent.Status) {
                                        CompositionLocalProvider(
                                            LocalComponentAppearance provides
                                                LocalComponentAppearance.current.copy(
                                                    showMedia = false,
                                                    showLinkPreview = false,
                                                ),
                                            LocalUriHandler provides uriHandler,
                                        ) {
                                            CommonStatusComponent(
                                                item = content,
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
                                            state.save(medias[state.currentPage])
                                        }
                                    }
                                } else {
                                    state.medias.onSuccess { medias ->
                                        state.save(medias[state.currentPage])
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
                    if (medias[state.currentPage] is UiMedia.Image) {
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
                                        state.shareMedia(medias[state.currentPage])
                                        state.setShowSheet(false)
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
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerControl(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
) {
    val playPauseButtonState = rememberPlayPauseButtonState(player)
    var isLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(player) {
        while (!isLoaded) {
            isLoaded = player.isPlaying
            awaitFrame()
        }
    }
    Row(
        modifier = modifier,
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
                mutableStateOf(0f)
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

@OptIn(ExperimentalTelephotoApi::class)
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
    val scope = rememberCoroutineScope()
    val zoomableState =
        rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 10f))
    LaunchedEffect(zoomableState.zoomFraction) {
        zoomableState.zoomFraction?.let {
            setLockPager(it > 0.01f)
        } ?: setLockPager(false)
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
        onDoubleClick = DoubleClickToZoomListener.cycle(2f),
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
    videoDownloadHelper: VideoDownloadHelper = koinInject(),
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
    val state =
        remember(statusKey) {
            StatusPresenter(accountType = accountType, statusKey = statusKey)
        }.invoke()
    var medias: UiState<ImmutableList<UiMedia>> by remember {
        mutableStateOf(UiState.Loading())
    }
    // prevent media change when medias is loaded
    if (!medias.isSuccess) {
        LaunchedEffect(state) {
            state.status
                .onSuccess {
                    medias =
                        UiState.Success(
                            (it.content as? UiTimeline.ItemContent.Status)
                                ?.images
                                .orEmpty()
                                .toImmutableList(),
                        )
                }
        }
    }
    var currentPage by remember {
        mutableIntStateOf(initialIndex)
    }
    object {
        val status = state.status
        val medias = medias
        val showUi = showUi
        val currentPage = currentPage
        val lockPager = lockPager
        val showSheet = showSheet

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

        fun save(data: UiMedia) {
            val status = (state.status.takeSuccess()?.content as? UiTimeline.ItemContent.Status)
            if (status != null) {
                val statusKey = status.statusKey.toString()
                val userHandle = status.user?.handle ?: "unknown"
                val fileName = data.getFileName(statusKey, userHandle)

                when (data) {
                    is UiMedia.Audio -> download(data.url, fileName)
                    is UiMedia.Gif -> download(data.url, fileName)
                    is UiMedia.Image -> save(data.url, fileName)
                    is UiMedia.Video -> download(data.url, fileName)
                }
            }
        }

        fun shareMedia(data: UiMedia) {
            when (data) {
                is UiMedia.Audio -> Unit
                is UiMedia.Gif -> Unit
                is UiMedia.Image -> {
                    scope.launch {
                        context.imageLoader.diskCache?.openSnapshot(data.url)?.use {
                            val originFile = it.data.toFile()
                            val targetFile =
                                File(
                                    context.cacheDir,
                                    data.url.substringAfterLast("/"),
                                )
                            originFile.copyTo(targetFile, overwrite = true)
                            val uri =
                                FileProvider.getUriForFile(
                                    context,
                                    context.packageName + ".provider",
                                    targetFile,
                                )
                            val intent =
                                Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    setDataAndType(
                                        uri,
                                        "image/*",
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            context.startActivity(
                                Intent.createChooser(
                                    intent,
                                    context.getString(R.string.media_menu_share_image),
                                ),
                            )
                        } ?: run {
                            withContext(Dispatchers.Main) {
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

                is UiMedia.Video -> Unit
            }
        }

        fun download(
            uri: String,
            fileName: String,
        ) {
            scope.launch {
                videoDownloadHelper.downloadVideo(
                    uri = uri,
                    fileName = fileName,
                    callback =
                        object : VideoDownloadHelper.DownloadCallback {
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
                    saveByteArrayToDownloads(context, byteArray, fileName)
                    withContext(Dispatchers.Main) {
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.media_save_success),
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
