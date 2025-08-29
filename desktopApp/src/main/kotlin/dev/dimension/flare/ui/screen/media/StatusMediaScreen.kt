package dev.dimension.flare.ui.screen.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.FloppyDisk
import compose.icons.fontawesomeicons.solid.UpRightAndDownLeftFromCenter
import dev.dimension.flare.Res
import dev.dimension.flare.media_fullscreen
import dev.dimension.flare.media_save
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.ComponentAppearance
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.status.MediaItem
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.StatusPresenter
import dev.dimension.flare.ui.presenter.status.StatusState
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.LocalComposeWindow
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.GridViewItem
import io.github.composefluent.component.HorizontalFlipView
import io.github.composefluent.component.SubtleButton
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.Viewport
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.spatial.CoordinateSpace
import me.saket.telephoto.zoomable.zoomable
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource
import java.awt.FileDialog

@Composable
internal fun WindowScope.StatusMediaScreen(
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
    FlareTheme(
        isDarkTheme = true,
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
                    ) {
                        medias.size
                    }
                HorizontalFlipView(
                    state = pagerState,
                    enabled = state.lockPager,
                    modifier =
                        Modifier.fillMaxSize(),
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
                                onClick = {
                                    state.setShowThumbnailList(!state.showThumbnailList)
                                },
                            )

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
                AnimatedVisibility(
                    state.showThumbnailList,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .background(FluentTheme.colors.background.layer.default)
                                .height(48.dp)
                                .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
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
                        SubtleButton(
                            onClick = {
                                if (window != null) {
                                    val current = window.placement
                                    if (current == WindowPlacement.Fullscreen) {
                                        window.placement = WindowPlacement.Floating
                                    } else {
                                        window.placement = WindowPlacement.Fullscreen
                                    }
                                }
                            },
                            content = {
                                FAIcon(
                                    FontAwesomeIcons.Solid.UpRightAndDownLeftFromCenter,
                                    contentDescription = stringResource(Res.string.media_fullscreen),
                                )
                            },
                        )
                    }
                }

                AnimatedVisibility(
                    state.showThumbnailList,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    LazyRow(
                        modifier =
                            Modifier
                                .background(FluentTheme.colors.background.layer.default)
                                .height(96.dp)
                                .padding(8.dp),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp,
                                Alignment.CenterHorizontally,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                    }
                }
            }
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
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isFocused) {
        if (isFocused) {
            focusRequester.requestFocus()
        } else {
            focusRequester.freeFocus()
        }
    }
    val zoomableState =
        rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 10f))
    LaunchedEffect(zoomableState.zoomFraction) {
        zoomableState.zoomFraction?.let {
            setLockPager(it > 0.01f)
        } ?: setLockPager(false)
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

    SubcomposeAsyncImage(
        model =
            ImageRequest
                .Builder(LocalPlatformContext.current)
                .data(url)
                .placeholderMemoryCacheKey(previewUrl)
                .crossfade(1_000)
                .size(Size.ORIGINAL)
                .build(),
        contentDescription = description,
        modifier =
            modifier
                .focusRequester(focusRequester)
                .zoomable(
                    state = zoomableState,
                    onClick = {
                        onClick?.invoke()
                    },
                ),
        contentScale = contentScale,
        alignment = alignment,
    )
}

@Composable
private fun presenter(
    accountType: AccountType,
    statusKey: MicroBlogKey,
    window: ComposeWindow?,
) = run {
    var lockPager by remember { mutableStateOf(false) }
    var showThumbnailList by remember { mutableStateOf(false) }
    val state =
        remember(
            "StatusMediaScreen_${accountType}_$statusKey",
        ) {
            StatusPresenter(accountType = accountType, statusKey = statusKey)
        }.invoke()

    val medias =
        state.status.map {
            (it.content as? UiTimeline.ItemContent.Status)?.images.orEmpty().toImmutableList()
        }

    medias.onSuccess {
        LaunchedEffect(it.size) {
            if (it.size > 1) {
                showThumbnailList = true
            }
        }
    }

    object : StatusState by state {
        val medias = medias
        val lockPager = lockPager
        val showThumbnailList = showThumbnailList

        fun setLockPager(value: Boolean) {
            lockPager = value
        }

        fun setShowThumbnailList(value: Boolean) {
            showThumbnailList = value
        }

        fun save(item: UiMedia) {
            val url = item.url
            val fileName = url.substring(url.lastIndexOf("/") + 1)
            FileDialog(window).apply {
                mode = FileDialog.SAVE
                file = fileName
                isVisible = true
                val dir = directory
                val file = file
                if (!dir.isNullOrEmpty() && !file.isNullOrEmpty()) {
                }
            }
        }
    }
}
