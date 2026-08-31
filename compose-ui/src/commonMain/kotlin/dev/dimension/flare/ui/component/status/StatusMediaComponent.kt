package dev.dimension.flare.ui.component.status

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CirclePlay
import compose.icons.fontawesomeicons.solid.Copy
import compose.icons.fontawesomeicons.solid.Download
import compose.icons.fontawesomeicons.solid.EyeSlash
import compose.icons.fontawesomeicons.solid.ShareNodes
import dev.dimension.flare.common.SystemUtils
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.hide_sensitive_media
import dev.dimension.flare.compose.ui.media_menu_copy_link
import dev.dimension.flare.compose.ui.media_menu_download
import dev.dimension.flare.compose.ui.media_menu_download_all
import dev.dimension.flare.compose.ui.media_menu_share_image
import dev.dimension.flare.compose.ui.media_view_alt_text
import dev.dimension.flare.compose.ui.show_sensitive_media
import dev.dimension.flare.compose.ui.status_sensitive_media
import dev.dimension.flare.data.model.TimelineMediaLayout
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.ui.component.AdaptiveGrid
import dev.dimension.flare.ui.component.AudioPlayer
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.accessibleDescription
import dev.dimension.flare.ui.component.platform.LocalWifiState
import dev.dimension.flare.ui.component.platform.PlatformCircularProgressIndicator
import dev.dimension.flare.ui.component.platform.PlatformDropdownMenu
import dev.dimension.flare.ui.component.platform.PlatformDropdownMenuItem
import dev.dimension.flare.ui.component.platform.PlatformDropdownMenuScope
import dev.dimension.flare.ui.component.platform.PlatformFlyoutContainer
import dev.dimension.flare.ui.component.platform.PlatformIconButton
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.PlatformVideoPlayer
import dev.dimension.flare.ui.humanizer.humanize
import dev.dimension.flare.ui.model.TimelineCarouselLayout
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import dev.dimension.flare.ui.theme.PlatformTheme
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun StatusMediaComponent(
    post: UiTimelineV2.Post,
    data: ImmutableList<UiMedia>,
    onMediaClick: (UiMedia) -> Unit,
    sensitive: Boolean,
    shape: Shape,
    allowCarousel: Boolean = false,
    carouselLeadingPadding: Dp = 0.dp,
    carouselTrailingPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val appearanceSettings = LocalTimelineAppearance.current
    var hideSensitive by remember(appearanceSettings.showSensitiveContent) {
        mutableStateOf(sensitive && !appearanceSettings.showSensitiveContent)
    }
    val showSensitiveButton = sensitive && !appearanceSettings.showSensitiveContent
    val usesCarousel =
        allowCarousel &&
            appearanceSettings.mediaLayout == TimelineMediaLayout.Carousel &&
            data.size > 1
    val mediaContentModifier =
        Modifier
            .let {
                if (hideSensitive && SystemUtils.isBlurSupported) {
                    it.blur(32.dp)
                } else {
                    it
                }
            }.let {
                if (hideSensitive) {
                    it.clearAndSetSemantics { }
                } else {
                    it
                }
            }
    val showSensitiveMediaLabel = stringResource(Res.string.show_sensitive_media)
    val hideSensitiveMediaLabel = stringResource(Res.string.hide_sensitive_media)
    Box(
        modifier =
            if (usesCarousel) {
                modifier.expandHorizontally(
                    leading = carouselLeadingPadding,
                    trailing = carouselTrailingPadding,
                )
            } else {
                modifier.clip(shape)
            },
    ) {
        if (usesCarousel) {
            BoxWithConstraints(
                modifier =
                    mediaContentModifier
                        .fillMaxWidth(),
            ) {
                val layoutSpec =
                    remember(
                        data.size,
                        data[0].carouselAspectRatio,
                        data[1].carouselAspectRatio,
                    ) {
                        TimelineCarouselLayout.spec(
                            mediaCount = data.size,
                            firstAspectRatio = data[0].carouselAspectRatio,
                            secondAspectRatio = data[1].carouselAspectRatio,
                        )
                    }
                val horizontalInsets = carouselLeadingPadding + carouselTrailingPadding
                val contentWidth = (maxWidth - horizontalInsets).coerceAtLeast(0.dp)
                val carouselHeight =
                    layoutSpec
                        .height(
                            viewportWidth = maxWidth.value,
                            horizontalInsets = horizontalInsets.value,
                            itemSpacing = CarouselItemSpacing.value,
                        ).dp

                LazyRow(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(carouselHeight),
                    contentPadding =
                        PaddingValues(
                            start = carouselLeadingPadding,
                            end = carouselTrailingPadding,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(CarouselItemSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(count = data.size) { index ->
                        val media = data[index]
                        val itemWidth =
                            layoutSpec
                                .itemWidth(
                                    contentWidth = contentWidth.value,
                                    height = carouselHeight.value,
                                    aspectRatio = media.timelineAspectRatio,
                                ).dp
                        StatusMediaItem(
                            post = post,
                            media = media,
                            mediaCount = data.size,
                            onMediaClick = onMediaClick,
                            hideSensitive = hideSensitive,
                            keepAspectRatio = false,
                            fillContainer = true,
                            modifier =
                                Modifier
                                    .fillParentMaxHeight()
                                    .width(itemWidth)
                                    .clip(shape),
                        )
                    }
                }
            }
        } else {
            AdaptiveGrid(
                content = {
                    data.fastForEach { media ->
                        StatusMediaItem(
                            post = post,
                            media = media,
                            mediaCount = data.size,
                            onMediaClick = onMediaClick,
                            hideSensitive = hideSensitive,
                            keepAspectRatio = data.size == 1 && appearanceSettings.expandMediaSize,
                        )
                    }
                },
                maxItems = if (appearanceSettings.limitMediaGridToNine) 9 else data.size,
                modifier = mediaContentModifier,
                expandedSize = appearanceSettings.expandMediaSize,
            )
        }
        if (showSensitiveButton) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .let {
                            if (hideSensitive && !SystemUtils.isBlurSupported) {
                                it.background(PlatformTheme.colorScheme.outline)
                            } else {
                                it
                            }
                        }.let {
                            if (hideSensitive) {
                                it
                                    .clickable(role = Role.Button) {
                                        hideSensitive = false
                                    }.semantics {
                                        contentDescription = showSensitiveMediaLabel
                                    }
                            } else {
                                it
                            }
                        }.padding(16.dp),
            ) {
                AnimatedContent(
                    hideSensitive,
                    modifier =
                        Modifier
                            .matchParentSize(),
                    label = "StatusMediaComponent",
                ) {
                    Box {
                        if (it) {
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.Center)
                                        .clip(PlatformTheme.shapes.medium)
                                        .background(PlatformTheme.colorScheme.card)
                                        .padding(16.dp),
                            ) {
                                PlatformText(
                                    text = stringResource(Res.string.status_sensitive_media),
                                )
                            }
                        } else {
                            PlatformIconButton(
                                onClick = {
                                    hideSensitive = true
                                },
                                modifier =
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .alpha(0.5f)
                                        .clip(PlatformTheme.shapes.medium)
                                        .background(PlatformTheme.colorScheme.card),
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.EyeSlash,
                                    contentDescription = hideSensitiveMediaLabel,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.expandHorizontally(
    leading: Dp,
    trailing: Dp,
): Modifier =
    layout { measurable, constraints ->
        if (!constraints.hasBoundedWidth) {
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
            }
        } else {
            val leadingPx = leading.roundToPx()
            val trailingPx = trailing.roundToPx()
            val expandedWidth = constraints.maxWidth + leadingPx + trailingPx
            val placeable =
                measurable.measure(
                    constraints.copy(
                        minWidth = expandedWidth,
                        maxWidth = expandedWidth,
                    ),
                )
            layout(constraints.maxWidth, placeable.height) {
                placeable.placeRelative(-leadingPx, 0)
            }
        }
    }

private val CarouselItemSpacing = 4.dp

private val UiMedia.carouselAspectRatio: Float
    get() {
        val ratio =
            when (this) {
                is UiMedia.Image -> width / height
                is UiMedia.Video -> width / height
                is UiMedia.Gif -> width / height
                is UiMedia.Audio -> 0f
            }
        return ratio.takeIf { it.isFinite() && it > 0f } ?: 0f
    }

private val UiMedia.timelineAspectRatio: Float
    get() = carouselAspectRatio.takeIf { it > 0f } ?: 1f

@Composable
private fun StatusMediaItem(
    post: UiTimelineV2.Post,
    media: UiMedia,
    mediaCount: Int,
    onMediaClick: (UiMedia) -> Unit,
    hideSensitive: Boolean,
    keepAspectRatio: Boolean,
    modifier: Modifier = Modifier,
    fillContainer: Boolean = false,
) {
    val uriHandler = LocalUriHandler.current
    val viewAltTextLabel = stringResource(Res.string.media_view_alt_text)
    val appearanceSettings = LocalTimelineAppearance.current
    val mediaActionConfig = LocalTimelineMediaActionConfig.current
    var isMenuExpanded by remember(media.url) {
        mutableStateOf(false)
    }
    Box(modifier = modifier) {
        CompositionLocalProvider(
            LocalTimelineAppearance provides
                appearanceSettings.copy(
                    videoAutoplay =
                        if (hideSensitive) {
                            VideoAutoplay.NEVER
                        } else {
                            appearanceSettings.videoAutoplay
                        },
                ),
        ) {
            val mediaModifier =
                Modifier
                    .let { if (fillContainer) it.fillMaxSize() else it }
                    .clipToBounds()
                    .pointerHoverIcon(PointerIcon.Hand)
            if (mediaActionConfig != null && !hideSensitive) {
                TimelineMediaMenuBox(
                    expanded = isMenuExpanded,
                    onExpandedChange = {
                        isMenuExpanded = it
                    },
                    onClick = {
                        onMediaClick(media)
                    },
                    modifier = mediaModifier,
                    menu = {
                        TimelineMediaDropdownMenu(
                            expanded = isMenuExpanded,
                            media = media,
                            showDownloadAll = mediaCount > 1,
                            mediaActionConfig = mediaActionConfig,
                            onDismissRequest = {
                                isMenuExpanded = false
                            },
                            onAction = { action ->
                                isMenuExpanded = false
                                mediaActionConfig.handler.handle(
                                    post = post,
                                    media = media,
                                    action = action,
                                )
                            },
                        )
                    },
                ) {
                    MediaItem(
                        media = media,
                        modifier = if (fillContainer) Modifier.fillMaxSize() else Modifier,
                        keepAspectRatio = keepAspectRatio,
                    )
                }
            } else {
                MediaItem(
                    media = media,
                    modifier =
                        mediaModifier.clickable(role = Role.Button) {
                            onMediaClick(media)
                        },
                    keepAspectRatio = keepAspectRatio,
                )
            }
        }
        if (!media.description.isNullOrBlank() && !hideSensitive) {
            PlatformFlyoutContainer(
                modifier = Modifier.align(Alignment.BottomEnd),
                content = { requestShowFlyout ->
                    PlatformText(
                        text = "ALT",
                        modifier =
                            Modifier
                                .pointerHoverIcon(PointerIcon.Hand)
                                .padding(16.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.75f),
                                    shape = PlatformTheme.shapes.medium,
                                ).padding(
                                    horizontal = 8.dp,
                                    vertical = 2.dp,
                                ).clickable(role = Role.Button) {
                                    if (!requestShowFlyout.invoke()) {
                                        media.description?.let {
                                            uriHandler.openUri(DeeplinkRoute.Status.AltText(it).toUri())
                                        }
                                    }
                                }.semantics {
                                    contentDescription = viewAltTextLabel
                                },
                        color = Color.White,
                    )
                },
                flyout = {
                    PlatformText(
                        text = media.description ?: "",
                        modifier =
                            Modifier
                                .padding(8.dp)
                                .widthIn(max = 240.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun TimelineMediaDropdownMenu(
    expanded: Boolean,
    media: UiMedia,
    showDownloadAll: Boolean,
    mediaActionConfig: TimelineMediaActionConfig,
    onDismissRequest: () -> Unit,
    onAction: (TimelineMediaMenuAction) -> Unit,
) {
    PlatformDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        TimelineMediaMenuItem(
            label = stringResource(Res.string.media_menu_download),
            icon = {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            onClick = {
                onAction(TimelineMediaMenuAction.Download)
            },
        )
        if (showDownloadAll) {
            TimelineMediaMenuItem(
                label = stringResource(Res.string.media_menu_download_all),
                icon = {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    onAction(TimelineMediaMenuAction.DownloadAll)
                },
            )
        }
        if (media is UiMedia.Image && mediaActionConfig.showShareImage) {
            TimelineMediaMenuItem(
                label = stringResource(Res.string.media_menu_share_image),
                icon = {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.ShareNodes,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    onAction(TimelineMediaMenuAction.ShareImage)
                },
            )
        }
        TimelineMediaMenuItem(
            label = stringResource(Res.string.media_menu_copy_link),
            icon = {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.Copy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            onClick = {
                onAction(TimelineMediaMenuAction.CopyLink)
            },
        )
    }
}

@Composable
private fun PlatformDropdownMenuScope.TimelineMediaMenuItem(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    PlatformDropdownMenuItem(
        text = {
            PlatformText(text = label)
        },
        leadingIcon = icon,
        onClick = onClick,
    )
}

@Composable
public fun MediaItem(
    media: UiMedia,
    modifier: Modifier = Modifier,
    keepAspectRatio: Boolean = true,
    showCountdown: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val appearanceSettings = LocalTimelineAppearance.current
    val accessibleDescription = media.accessibleDescription()
    when (media) {
        is UiMedia.Image -> {
            NetworkImage(
                model = media.previewUrl,
                contentDescription = accessibleDescription,
                contentScale = contentScale,
                customHeaders = media.customHeaders,
                modifier =
                    modifier
                        .fillMaxWidth()
                        .let {
                            if (keepAspectRatio) {
                                it.aspectRatio(
                                    media.aspectRatio,
                                    matchHeightConstraintsFirst = media.aspectRatio > 1f,
                                )
                            } else {
                                it
                            }
                        },
            )
        }

        is UiMedia.Video -> {
            val wifiState = LocalWifiState.current
            val shouldPlay =
                remember(appearanceSettings.videoAutoplay, wifiState) {
                    appearanceSettings.videoAutoplay == VideoAutoplay.ALWAYS ||
                        (appearanceSettings.videoAutoplay == VideoAutoplay.WIFI && wifiState)
                }
            if (shouldPlay) {
                PlatformVideoPlayer(
                    contentScale = contentScale,
                    uri = media.url,
                    customHeaders = media.customHeaders,
                    muted = true,
                    previewUri = media.thumbnailUrl,
                    contentDescription = accessibleDescription,
                    modifier =
                        modifier
                            .fillMaxWidth()
                            .let {
                                if (keepAspectRatio) {
                                    it.aspectRatio(
                                        media.aspectRatio,
                                        matchHeightConstraintsFirst = media.aspectRatio > 1f,
                                    )
                                } else {
                                    it
                                }
                            },
                    loadingPlaceholder = {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            NetworkImage(
                                contentScale = contentScale,
                                model = media.thumbnailUrl,
                                customHeaders = media.customHeaders,
                                contentDescription = accessibleDescription,
                                modifier =
                                    Modifier
                                        .fillMaxWidth(),
                            )
                        }
                        Box(
                            modifier =
                                Modifier
                                    .padding(16.dp)
                                    .background(
                                        Color.Black.copy(alpha = 0.5f),
                                        shape = PlatformTheme.shapes.medium,
                                    ).padding(horizontal = 8.dp, vertical = 4.dp)
                                    .align(Alignment.BottomStart),
                            contentAlignment = Alignment.Center,
                        ) {
                            PlatformCircularProgressIndicator(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .size(16.dp),
                                color = Color.White,
                            )
                        }
                    },
                    errorContent = {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            NetworkImage(
                                contentScale = contentScale,
                                model = media.thumbnailUrl,
                                customHeaders = media.customHeaders,
                                contentDescription = accessibleDescription,
                                modifier =
                                    Modifier
                                        .fillMaxWidth(),
                            )
                        }
                        Box(
                            modifier =
                                Modifier
                                    .padding(16.dp)
                                    .background(
                                        Color.Black.copy(alpha = 0.5f),
                                        shape = PlatformTheme.shapes.medium,
                                    ).padding(horizontal = 8.dp, vertical = 4.dp)
                                    .align(Alignment.BottomStart),
                            contentAlignment = Alignment.Center,
                        ) {
                            FAIcon(
                                FontAwesomeIcons.Solid.CirclePlay,
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .size(16.dp),
                                tint = Color.White,
                            )
                        }
                    },
                    remainingTimeContent =
                        if (showCountdown) {
                            {
                                Box(
                                    modifier =
                                        Modifier
                                            .padding(16.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.5f),
                                                shape = PlatformTheme.shapes.medium,
                                            ).padding(horizontal = 8.dp, vertical = 4.dp)
                                            .align(Alignment.BottomStart),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PlatformText(
                                        text =
                                            remember(it) {
                                                it.milliseconds.humanize()
                                            },
                                        color = Color.White,
                                        style = PlatformTheme.typography.caption,
                                    )
                                }
                            }
                        } else {
                            null
                        },
                )
            } else {
                Box(
                    modifier = modifier,
                ) {
                    NetworkImage(
                        contentScale = contentScale,
                        model = media.thumbnailUrl,
                        customHeaders = media.customHeaders,
                        contentDescription = accessibleDescription,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .let {
                                    if (keepAspectRatio) {
                                        it.aspectRatio(
                                            media.aspectRatio,
                                            matchHeightConstraintsFirst = media.aspectRatio > 1f,
                                        )
                                    } else {
                                        it
                                    }
                                },
                    )
                    Box(
                        modifier =
                            Modifier
                                .padding(16.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.5f),
                                    shape = PlatformTheme.shapes.medium,
                                ).padding(horizontal = 8.dp, vertical = 4.dp)
                                .align(Alignment.BottomStart),
                        contentAlignment = Alignment.Center,
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.CirclePlay,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(16.dp),
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        is UiMedia.Audio -> {
            AudioPlayer(
                uri = media.url,
                previewUri = media.previewUrl,
                contentDescription = accessibleDescription,
                modifier = modifier,
            )
        }

        is UiMedia.Gif -> {
            NetworkImage(
                model = media.url,
                contentDescription = accessibleDescription,
                contentScale = contentScale,
                customHeaders = media.customHeaders,
                modifier =
                    modifier
                        .fillMaxWidth()
                        .let {
                            if (keepAspectRatio) {
                                it.aspectRatio(
                                    media.aspectRatio,
                                    matchHeightConstraintsFirst = media.aspectRatio > 1f,
                                )
                            } else {
                                it
                            }
                        },
            )
        }
    }
}
