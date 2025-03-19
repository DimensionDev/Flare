package dev.dimension.flare.ui.screen.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.component.GridViewItem
import com.konyaco.fluent.component.HorizontalFlipView
import com.konyaco.fluent.component.SubtleButton
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Chalkboard
import compose.icons.fontawesomeicons.solid.FloppyDisk
import compose.icons.fontawesomeicons.solid.UpRightAndDownLeftFromCenter
import dev.dimension.flare.Res
import dev.dimension.flare.media_fullscreen
import dev.dimension.flare.media_hide_thumbnail_list
import dev.dimension.flare.media_save
import dev.dimension.flare.media_show_thumbnail_list
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.status.MediaItem
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.StatusPresenter
import dev.dimension.flare.ui.presenter.status.StatusState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource
import javax.swing.JFileChooser
import kotlin.collections.orEmpty

@Composable
internal fun StatusMediaScreen(
    accountType: AccountType,
    statusKey: MicroBlogKey,
    index: Int,
    window: ComposeWindow,
) {
    val scope = rememberCoroutineScope()
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
                        .weight(1f),
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

                    else ->
                        MediaItem(
                            media = media,
                            modifier = Modifier.fillMaxSize(),
                        )
                }
            }
            AnimatedVisibility(
                state.showThumbnailList,
            ) {
                LazyRow(
                    modifier =
                        Modifier
                            .fillMaxWidth()
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
            Row(
                modifier =
                    Modifier
                        .height(48.dp)
                        .fillMaxWidth()
                        .background(FluentTheme.colors.background.layer.default)
                        .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(modifier = Modifier.weight(1f))
                if (medias.size > 1) {
                    SubtleButton(
                        onClick = {
                            state.setShowThumbnailList(!state.showThumbnailList)
                        },
                        content = {
                            FAIcon(
                                FontAwesomeIcons.Solid.Chalkboard,
                                contentDescription =
                                    if (state.showThumbnailList) {
                                        stringResource(Res.string.media_hide_thumbnail_list)
                                    } else {
                                        stringResource(Res.string.media_show_thumbnail_list)
                                    },
                            )
                        },
                    )
                }
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
                        val current = window.placement
                        if (current == WindowPlacement.Fullscreen) {
                            window.placement = WindowPlacement.Floating
                        } else {
                            window.placement = WindowPlacement.Fullscreen
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
    }
}

@Composable
private fun ImageItem(
    url: String,
    previewUrl: String,
    description: String?,
    setLockPager: (Boolean) -> Unit,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
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
        rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 10f, minZoomFactor = 0.01f))
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
                .zoomable(state = zoomableState),
        contentScale = contentScale,
        alignment = alignment,
    )
}

@Composable
private fun presenter(
    accountType: AccountType,
    statusKey: MicroBlogKey,
    window: ComposeWindow,
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
            val fileChooser = JFileChooser()
            val url = item.url
            val fileName = url.substring(url.lastIndexOf("/") + 1)
            fileChooser.selectedFile = java.io.File(fileName)
            if (fileChooser.showSaveDialog(window) == JFileChooser.APPROVE_OPTION) {
                val file = fileChooser.selectedFile
                // TODO: Save file
            }
        }
    }
}
