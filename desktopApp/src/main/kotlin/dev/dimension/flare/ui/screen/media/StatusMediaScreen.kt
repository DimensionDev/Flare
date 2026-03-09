package dev.dimension.flare.ui.screen.media

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.times
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import androidx.compose.ui.unit.toOffset
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.FloppyDisk
import compose.icons.fontawesomeicons.solid.Pause
import compose.icons.fontawesomeicons.solid.Play
import dev.dimension.flare.Res
import dev.dimension.flare.common.DesktopDownloadManager
import dev.dimension.flare.common.FlareHardwareShortcutDetector
import dev.dimension.flare.common.FlareHardwareShortcutsElement
import dev.dimension.flare.media_save
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.ComponentAppearance
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.status.MediaItem
import dev.dimension.flare.ui.humanizer.humanize
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.getFileName
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.StatusPresenter
import dev.dimension.flare.ui.presenter.status.StatusState
import dev.dimension.flare.ui.theme.LocalComposeWindow
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.CommandBarSeparator
import io.github.composefluent.component.GridViewItem
import io.github.composefluent.component.HorizontalFlipView
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.ProgressRing
import io.github.composefluent.component.Slider
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.github.composefluent.surface.Card
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import io.github.kdroidfilter.composemediaplayer.rememberVideoPlayerState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.HardwareShortcutsSpec
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.ZoomableContentLocation.SameAsLayoutBounds
import me.saket.telephoto.zoomable.ZoomableContentLocation.Unspecified
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import moe.tlaster.precompose.molecule.producePresenter
import org.apache.commons.lang3.SystemUtils
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import java.awt.FileDialog
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun StatusMediaScreen(
    accountType: AccountType,
    statusKey: MicroBlogKey,
    index: Int,
) {
    val scope = rememberCoroutineScope()
    val window = LocalComposeWindow.current
    val state by producePresenter(
        "StatusMediaScreen_${accountType}_$statusKey",
    ) {
        presenter(
            accountType = accountType,
            statusKey = statusKey,
            window = window,
        )
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize(),
    ) {
        state.medias.onSuccess { medias ->
            val pagerState =
                rememberPagerState(
                    initialPage = index,
                ) {
                    medias.size
                }
            HorizontalFlipView(
                state = pagerState,
                enabled = state.lockPager,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onKeyEvent {
                            when (it.key) {
                                androidx.compose.ui.input.key.Key.DirectionRight -> {
                                    if (pagerState.currentPage < pagerState.pageCount - 1) {
                                        scope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }

                                androidx.compose.ui.input.key.Key.DirectionLeft -> {
                                    if (pagerState.currentPage > 0) {
                                        scope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }

                                else -> false
                            }
                        },
            ) {
                val media = medias[it]
                when (media) {
                    is UiMedia.Image ->
                        ImageItem(
                            modifier = Modifier.fillMaxSize(),
                            url = media.url,
                            previewUrl = media.previewUrl,
                            description = media.description,
                            isFocused = pagerState.currentPage == it,
                            setLockPager = state::setLockPager,
                        )

                    is UiMedia.Video -> {
                        if (pagerState.currentPage == it) {
                            VideoItem(
                                url = media.url,
                                thumbnailUrl = media.thumbnailUrl,
                                description = media.description,
                                modifier =
                                    Modifier
                                        .fillMaxSize(),
                            )
                        } else {
                            ImageItem(
                                modifier = Modifier.fillMaxSize(),
                                url = media.thumbnailUrl,
                                previewUrl = media.thumbnailUrl,
                                description = media.description,
                                isFocused = pagerState.currentPage == it,
                                setLockPager = state::setLockPager,
                            )
                        }
                    }

                    else ->
                        CompositionLocalProvider(
                            LocalComponentAppearance provides
                                LocalComponentAppearance
                                    .current
                                    .copy(
                                        videoAutoplay = ComponentAppearance.VideoAutoplay.ALWAYS,
                                    ),
                        ) {
                            MediaItem(
                                media = media,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                }
            }
            LazyRow(
                modifier =
                    Modifier
                        .background(FluentTheme.colors.background.layer.default)
                        .height(64.dp)
                        .padding(8.dp)
                        .fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp,
                        Alignment.CenterHorizontally,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (medias.size > 1) {
                    items(medias.size) { index ->
                        val media = medias[index]
                        GridViewItem(
                            selected = pagerState.currentPage == index,
                            onSelectedChange = {
                                if (it) {
                                    scope.launch {
                                        pagerState.scrollToPage(index)
                                    }
                                }
                            },
                        ) {
                            NetworkImage(
                                model =
                                    when (media) {
                                        is UiMedia.Audio -> media.previewUrl
                                        is UiMedia.Gif -> media.previewUrl
                                        is UiMedia.Image -> media.previewUrl
                                        is UiMedia.Video -> media.thumbnailUrl
                                    },
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .aspectRatio(1f),
                            )
                        }
                    }
                    item {
                        CommandBarSeparator()
                    }
                }
                item {
                    SubtleButton(
                        onClick = {
                            val current = medias[pagerState.currentPage]
                            state.save(current)
                        },
                        content = {
                            FAIcon(
                                FontAwesomeIcons.Solid.FloppyDisk,
                                contentDescription = stringResource(Res.string.media_save),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun VideoItem(
    url: String,
    thumbnailUrl: String,
    description: String?,
    modifier: Modifier = Modifier,
) {
    val playerState = rememberVideoPlayerState()
    DisposableEffect(Unit) {
        playerState.loop = true
        playerState.openUri(url)
        onDispose {
            playerState.stop()
        }
    }
    var showControls by remember { mutableStateOf(true) }
    Box(
        modifier = modifier,
    ) {
        AnimatedContent(
            playerState.isLoading && playerState.sliderPos == 0f,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            modifier =
                Modifier.clickable {
                    showControls = !showControls
                },
        ) { isLoading ->
            if (!isLoading) {
                VideoPlayerSurface(
                    playerState = playerState,
                )
            } else {
                NetworkImage(
                    model = thumbnailUrl,
                    contentDescription = description,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        AnimatedVisibility(
            showControls,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .widthIn(max = 480.dp),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Card(
                modifier = Modifier,
            ) {
                PlayerControl(
                    state = playerState,
                )
            }
        }
    }
}

@Composable
private fun PlayerControl(
    state: VideoPlayerState,
    modifier: Modifier = Modifier,
) {
    val playerState by rememberUpdatedState(state)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (playerState.isLoading) {
            ProgressBar(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            )
        } else {
            SubtleButton(
                onClick = {
                    if (playerState.isPlaying) {
                        playerState.pause()
                    } else {
                        playerState.play()
                    }
                },
                disabled = playerState.isLoading,
            ) {
                FAIcon(
                    if (!playerState.isPlaying) {
                        FontAwesomeIcons.Solid.Play
                    } else {
                        FontAwesomeIcons.Solid.Pause
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (!playerState.sliderPos.isNaN()) {
                Slider(
                    value = playerState.sliderPos,
                    onValueChange = {
                        playerState.sliderPos = it
                        playerState.userDragging = true
                    },
                    onValueChangeFinished = {
                        playerState.userDragging = false
                        playerState.seekTo(playerState.sliderPos)
                    },
                    valueRange = 0f..1000f,
                    modifier = Modifier.weight(1f),
                    tooltipContent = {
                        val duration = state.metadata.duration
                        if (duration != null) {
                            Text(
                                (it.value / 1000f * duration)
                                    .roundToLong()
                                    .let {
                                        // https://github.com/kdroidFilter/ComposeMediaPlayer/issues/153
                                        if (SystemUtils.IS_OS_MAC_OSX) {
                                            it.seconds
                                        } else if (SystemUtils.IS_OS_LINUX) {
                                            it.nanoseconds
                                        } else {
                                            it.milliseconds
                                        }
                                    }.humanize(),
                            )
                        }
                    },
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            val currentTime by remember {
                derivedStateOf {
                    buildString {
                        append(playerState.positionText)
                        append(" / ")
                        append(playerState.durationText)
                    }
                }
            }
            Text(currentTime)
            Spacer(Modifier.width(screenHorizontalPadding))
        }
    }
}

@OptIn(ExperimentalTelephotoApi::class)
@Composable
internal fun ImageItem(
    url: String,
    previewUrl: String,
    description: String?,
    setLockPager: (Boolean) -> Unit,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val painter =
        rememberAsyncImagePainter(
            ImageRequest
                .Builder(LocalPlatformContext.current)
                .data(url)
                .placeholderMemoryCacheKey(previewUrl)
                .crossfade(1_000)
                .size(Size.ORIGINAL)
                .build(),
        )
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isFocused) {
        if (isFocused) {
            focusRequester.requestFocus()
        } else {
            focusRequester.freeFocus()
        }
    }
    val zoomableState =
        rememberZoomableState(
            zoomSpec = ZoomSpec(maxZoomFactor = 50f, minZoomFactor = 0.1f),
            hardwareShortcutsSpec =
                HardwareShortcutsSpec(
                    enabled = false,
                    shortcutDetector = FlareHardwareShortcutDetector,
                ),
        ).apply {
            LaunchedEffect(painter.intrinsicSize) {
                setContentLocation(
                    scaledInsideAndCenterAligned(painter.intrinsicSize),
                )
            }
        }
    LaunchedEffect(zoomableState.zoomFraction) {
        zoomableState.zoomFraction?.let {
            setLockPager(it > 0.01f)
        } ?: setLockPager(false)
    }
    LaunchedEffect(painter.intrinsicSize) {
        val aspectRatio = painter.intrinsicSize.height / painter.intrinsicSize.width
        val targetAspectRatio = 19.5f / 9f
        if (aspectRatio > targetAspectRatio) {
            zoomableState.contentAlignment = Alignment.TopCenter
            zoomableState.contentScale = ContentScale.FillWidth
        } else {
            zoomableState.contentAlignment = Alignment.Center
            zoomableState.contentScale = ContentScale.Fit
        }
    }

    val state by painter.state.collectAsState()
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter,
            contentDescription = description,
            modifier =
                Modifier
                    .matchParentSize()
                    .focusRequester(focusRequester)
                    .zoomable(
                        state = zoomableState,
                        onClick = {
                            onClick?.invoke()
                        },
                    ).then(FlareHardwareShortcutsElement(zoomableState))
                    .focusable(),
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
        )
        if (state is AsyncImagePainter.State.Loading) {
            ProgressRing()
        }
    }
}

@Composable
private fun presenter(
    accountType: AccountType,
    statusKey: MicroBlogKey,
    window: ComposeWindow?,
) = run {
    // io scope
    val scope: CoroutineScope = koinInject()
    val desktopDownloadManager: DesktopDownloadManager = koinInject()
    var lockPager by remember { mutableStateOf(false) }
    val state =
        remember(
            "StatusMediaScreen_${accountType}_$statusKey",
        ) {
            StatusPresenter(accountType = accountType, statusKey = statusKey)
        }.invoke()

    val medias =
        state.status.map {
            (it as? UiTimelineV2.Post)?.images.orEmpty().toImmutableList()
        }

    object : StatusState by state {
        val medias = medias
        val lockPager = lockPager

        fun setLockPager(value: Boolean) {
            lockPager = value
        }

        fun save(item: UiMedia) {
            val status = state.status.takeSuccess() as? UiTimelineV2.Post
            if (status != null) {
                val userHandle = status.user?.handle?.canonical ?: "unknown"
                val fileName = item.getFileName(statusKey.toString(), userHandle)
                FileDialog(window).apply {
                    mode = FileDialog.SAVE
                    file = fileName
                    isVisible = true
                    val dir = directory
                    val file = file
                    if (!dir.isNullOrEmpty() && !file.isNullOrEmpty()) {
                        scope.launch {
                            desktopDownloadManager.download(
                                url = item.url,
                                targetFile = java.io.File(dir, file),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Stable
private fun scaledInsideAndCenterAligned(size: androidx.compose.ui.geometry.Size?): ZoomableContentLocation =
    when {
        size == null -> Unspecified
        size.isUnspecified -> SameAsLayoutBounds
        else -> {
            RelativeContentLocation(
                size = size,
                scale = ContentScale.Fit,
                alignment = Alignment.Center,
            )
        }
    }

@Immutable
private data class RelativeContentLocation(
    private val size: androidx.compose.ui.geometry.Size,
    private val scale: ContentScale,
    private val alignment: Alignment,
) : ZoomableContentLocation {
    @Deprecated("No longer used")
    override fun size(layoutSize: androidx.compose.ui.geometry.Size): androidx.compose.ui.geometry.Size {
        val scaleFactor =
            if (size.isEmpty()) {
                androidx.compose.ui.layout
                    .ScaleFactor(0f, 0f)
            } else {
                scale.computeScaleFactor(
                    srcSize = size,
                    dstSize = layoutSize,
                )
            }
        return size * scaleFactor
    }

    override fun location(
        layoutSize: androidx.compose.ui.geometry.Size,
        direction: LayoutDirection,
    ): Rect {
        check(!layoutSize.isEmpty()) { "Layout size is empty" }

        @Suppress("DEPRECATION")
        val scaledSize = size(layoutSize)
        val alignedOffset =
            alignment.align(
                size = scaledSize.roundToIntSize(),
                space = layoutSize.roundToIntSize(),
                layoutDirection = direction,
            )
        return Rect(
            offset = alignedOffset.toOffset(),
            size = scaledSize,
        )
    }
}
