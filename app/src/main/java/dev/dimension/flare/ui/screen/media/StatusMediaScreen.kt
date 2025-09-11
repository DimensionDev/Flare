package dev.dimension.flare.ui.screen.media

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import compose.icons.fontawesomeicons.solid.Xmark
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.rememberHazeState
import dev.dimension.flare.R
import dev.dimension.flare.common.VideoDownloadHelper
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.Glassify
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.component.VideoPlayer
import dev.dimension.flare.ui.component.VideoPlayerPool
import dev.dimension.flare.ui.component.status.QuotedStatus
import dev.dimension.flare.ui.humanizer.humanize
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.StatusPresenter
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.fornewid.placeholder.material3.placeholder
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.ExperimentalTelephotoApi
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
    ExperimentalHazeMaterialsApi::class,
)
@Composable
internal fun StatusMediaScreen(
    statusKey: MicroBlogKey,
    accountType: AccountType,
    index: Int,
    preview: String?,
    onDismiss: () -> Unit,
    toAltText: (UiMedia) -> Unit,
    playerPool: VideoPlayerPool = koinInject(),
) {
    val hazeState = rememberHazeState()
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
                state.medias.onSuccess { media ->
                    var lastPage by remember {
                        mutableIntStateOf(pagerState.currentPage)
                    }
                    LaunchedEffect(pagerState.currentPage) {
                        state.setCurrentPage(pagerState.currentPage)
                        if (lastPage != pagerState.currentPage) {
                            val player = playerPool.peek(media[pagerState.currentPage].url)
                            player?.play()
                            val lastPlayer = playerPool.peek(media[lastPage].url)
                            lastPlayer?.pause()

                            // handle pages around without lastPage
                            if (lastPage == pagerState.currentPage - 1 && pagerState.currentPage + 1 < pagerState.pageCount) {
                                val nextPlayer =
                                    playerPool.peek(media[pagerState.currentPage + 1].url)
                                nextPlayer?.pause()
                            } else if (lastPage == pagerState.currentPage + 1 && pagerState.currentPage - 1 >= 0) {
                                val prevPlayer =
                                    playerPool.peek(media[pagerState.currentPage - 1].url)
                                prevPlayer?.pause()
                            }

                            lastPage = pagerState.currentPage
                        }
                    }
                    // TODO: workaround: some video url might change after StatusPresenter load the status
                    DisposableEffect(pagerState.currentPage) {
                        val player = playerPool.peek(media[pagerState.currentPage].url)
                        onDispose {
                            player?.volume = 0f
                        }
                    }
                }
                Swiper(
                    state = swiperState,
                    modifier =
                        Modifier
                            .hazeSource(state = hazeState),
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
                                            aspectRatio = media.aspectRatio,
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
                                            setLockPager = {
                                                if (pagerState.currentPage == index) {
                                                    state.setLockPager(it)
                                                }
                                            },
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
                                            aspectRatio = media.aspectRatio,
                                            showControls = true,
                                            keepScreenOn = true,
                                            muted = false,
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
                }
                AnimatedVisibility(
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
                            hazeState = hazeState,
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
                                    hazeState = hazeState,
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
                                            state.save(medias[state.currentPage])
                                        }
                                    } else {
                                        state.save(medias[state.currentPage])
                                    }
                                },
                                hazeState = hazeState,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                            ) {
                                FAIcon(
                                    FontAwesomeIcons.Solid.Download,
                                    contentDescription = stringResource(id = R.string.media_menu_save),
                                )
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
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter),
                            enter = slideInVertically { it },
                            exit = slideOutVertically { it },
                        ) {
                            Glassify(
                                hazeState = hazeState,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.onBackground,
                            ) {
                                Column(
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
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    if (pagerState.pageCount > 1) {
                                        Row(
                                            modifier =
                                                Modifier
                                                    .padding(bottom = 8.dp),
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
                                            val player = playerPool.peek(current.url)
                                            if (player != null) {
                                                PlayerControl(
                                                    player,
                                                    modifier =
                                                        Modifier
                                                            .padding(end = screenHorizontalPadding),
                                                )
                                            }
                                        }
                                    }
                                    CompositionLocalProvider(
                                        LocalComponentAppearance provides
                                            LocalComponentAppearance.current.copy(
                                                showMedia = false,
                                            ),
                                    ) {
                                        QuotedStatus(
                                            data = content,
                                            showActions = true,
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
}

@androidx.annotation.OptIn(UnstableApi::class)
@ExperimentalMaterial3Api
@Composable
private fun PlayerControl(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
) {
    val playPauseButtonState = rememberPlayPauseButtonState(player)
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
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
    }
}

@OptIn(ExperimentalTelephotoApi::class)
@Composable
private fun ImageItem(
    url: String,
    previewUrl: String,
    description: String?,
    onClick: () -> Unit,
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
    val aspectRatio =
        remember(zoomableState.coordinateSystem.unscaledContentBounds) {
            with(zoomableState.coordinateSystem) {
                unscaledContentBounds.rectIn(CoordinateSpace.Viewport)
            }.let {
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
            showUi = !value
        }

        fun save(data: UiMedia) {
            when (data) {
                is UiMedia.Audio -> download(data.url)
                is UiMedia.Gif -> download(data.url)
                is UiMedia.Image -> save(data.url)
                is UiMedia.Video -> download(data.url)
            }
        }

        fun download(uri: String) {
            scope.launch {
                videoDownloadHelper.downloadVideo(
                    uri = uri,
                    fileName = uri.substringAfterLast("/"),
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
