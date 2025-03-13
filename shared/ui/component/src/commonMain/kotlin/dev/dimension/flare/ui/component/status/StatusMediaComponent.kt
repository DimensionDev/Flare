package dev.dimension.flare.ui.component.status

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CirclePlay
import compose.icons.fontawesomeicons.solid.EyeSlash
import dev.dimension.flare.ui.component.AdaptiveGrid
import dev.dimension.flare.ui.component.AudioPlayer
import dev.dimension.flare.ui.component.ComponentAppearance
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.Res
import dev.dimension.flare.ui.component.platform.PlatformCircularProgressIndicator
import dev.dimension.flare.ui.component.platform.PlatformIconButton
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.PlatformVideoPlayer
import dev.dimension.flare.ui.component.platform.rememberPlatformWifiState
import dev.dimension.flare.ui.component.status_sensitive_media
import dev.dimension.flare.ui.humanizer.humanize
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.theme.PlatformTheme
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun StatusMediaComponent(
    data: ImmutableList<UiMedia>,
    onMediaClick: (UiMedia) -> Unit,
    sensitive: Boolean,
    modifier: Modifier = Modifier,
) {
    val appearanceSettings = LocalComponentAppearance.current
    var hideSensitive by remember(appearanceSettings.showSensitiveContent) {
        mutableStateOf(sensitive && !appearanceSettings.showSensitiveContent)
    }
    val showSensitiveButton =
        remember(data, sensitive) {
            data.all { it is UiMedia.Image } && sensitive
        }
    Box(
        modifier =
            modifier
                .clip(PlatformTheme.shapes.medium),
    ) {
        AdaptiveGrid(
            content = {
                data.forEach { media ->
                    MediaItem(
                        media = media,
                        modifier =
                            Modifier
                                .clipToBounds()
//                                .sharedElement(
//                                    rememberSharedContentState(
//                                        when (media) {
//                                            is UiMedia.Image -> media.previewUrl
//                                            is UiMedia.Video -> media.thumbnailUrl
//                                            is UiMedia.Audio -> media.previewUrl ?: media.url
//                                            is UiMedia.Gif -> media.previewUrl
//                                        },
//                                    ),
//                                    animatedVisibilityScope = this@AnimatedVisibilityScope,
//                                )
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable {
                                    onMediaClick(media)
                                },
//                        keepAspectRatio = data.size == 1 && appearanceSettings.expandMediaSize,
                    )
                }
            },
            modifier =
                Modifier
                    .clip(PlatformTheme.shapes.medium)
                    .let {
                        if (hideSensitive) {
                            it.blur(32.dp)
                        } else {
                            it
                        }
//                        if (hideSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//                            it.blur(32.dp)
//                        } else {
//                            it
//                        }
                    },
            expandedSize = appearanceSettings.expandMediaSize,
        )
        if (showSensitiveButton) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .let {
                            it
//                            if (hideSensitive && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
//                                it.background(PlatformTheme.colorScheme.surfaceContainer)
//                            } else {
//                                it
//                            }
                        }.let {
                            if (hideSensitive) {
                                it.clickable {
                                    hideSensitive = false
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
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
public fun MediaItem(
    media: UiMedia,
    modifier: Modifier = Modifier,
    keepAspectRatio: Boolean = true,
    showCountdown: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val appearanceSettings = LocalComponentAppearance.current
    when (media) {
        is UiMedia.Image -> {
            NetworkImage(
                model = media.previewUrl,
                contentDescription = media.description,
                contentScale = contentScale,
                modifier =
                    modifier
                        .fillMaxSize()
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
            val wifiState by rememberPlatformWifiState()
            val shouldPlay =
                remember(appearanceSettings.videoAutoplay, wifiState) {
                    appearanceSettings.videoAutoplay == ComponentAppearance.VideoAutoplay.ALWAYS ||
                        (appearanceSettings.videoAutoplay == ComponentAppearance.VideoAutoplay.WIFI && wifiState)
                }
            if (shouldPlay) {
                PlatformVideoPlayer(
                    contentScale = contentScale,
                    uri = media.url,
                    muted = true,
                    previewUri = media.thumbnailUrl,
                    contentDescription = media.description,
                    modifier =
                        modifier
                            .fillMaxSize()
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
                                    .fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            NetworkImage(
                                contentScale = contentScale,
                                model = media.thumbnailUrl,
                                contentDescription = media.description,
                                modifier =
                                    Modifier
                                        .fillMaxSize(),
                            )
                        }
                        PlatformCircularProgressIndicator(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(24.dp)
                                    .size(24.dp),
                            color = Color.White,
                        )
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
                                                shape = PlatformTheme.shapes.small,
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
                        contentDescription = media.description,
                        modifier =
                            Modifier
                                .fillMaxSize()
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
                    FAIcon(
                        FontAwesomeIcons.Solid.CirclePlay,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                                .size(48.dp),
                        tint = PlatformTheme.colorScheme.card,
                    )
                }
            }
        }

        is UiMedia.Audio -> {
            AudioPlayer(
                uri = media.url,
                previewUri = media.previewUrl,
                contentDescription = media.description,
                modifier = modifier,
            )
        }

        is UiMedia.Gif ->
            PlatformVideoPlayer(
                contentScale = contentScale,
                uri = media.url,
                muted = true,
                previewUri = media.previewUrl,
                contentDescription = media.description,
                modifier =
                    modifier
                        .fillMaxSize()
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
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    NetworkImage(
                        contentScale = contentScale,
                        model = media.previewUrl,
                        contentDescription = media.description,
                        modifier =
                            Modifier
                                .fillMaxSize(),
                    )
                }
                PlatformCircularProgressIndicator(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(24.dp)
                            .size(24.dp),
                    color = Color.White,
                )
            }
    }
}
