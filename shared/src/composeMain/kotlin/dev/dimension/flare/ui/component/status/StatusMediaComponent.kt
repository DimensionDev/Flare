package dev.dimension.flare.ui.component.status

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.EyeSlash
import dev.dimension.flare.Res
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.status_sensitive_media
import dev.dimension.flare.ui.component.AdaptiveGrid
import dev.dimension.flare.ui.component.AudioPlayer
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.GifPlayer
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.VideoPlayer
import dev.dimension.flare.ui.model.UiMedia
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StatusMediaComponent(
    data: ImmutableList<UiMedia>,
    onMediaClick: (UiMedia) -> Unit,
    sensitive: Boolean,
    modifier: Modifier = Modifier,
) {
    val appearanceSettings = LocalAppearanceSettings.current
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
                .clip(MaterialTheme.shapes.medium),
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
                                .clickable {
                                    onMediaClick(media)
                                },
                        keepAspectRatio = data.size == 1 && appearanceSettings.expandMediaSize,
                    )
                }
            },
            modifier =
                Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .let {
//                        if (hideSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (hideSensitive) {
                            it.blur(32.dp)
                        } else {
                            it
                        }
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
//                                it.background(MaterialTheme.colorScheme.surfaceContainer)
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
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(16.dp),
                            ) {
                                Text(
                                    text = stringResource(Res.string.status_sensitive_media),
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    hideSensitive = true
                                },
                                modifier =
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .alpha(0.5f)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.surface),
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
fun MediaItem(
    media: UiMedia,
    modifier: Modifier = Modifier,
    keepAspectRatio: Boolean = true,
    showCountdown: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
) {
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
            VideoPlayer(
                data = media,
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

        is UiMedia.Audio ->
            AudioPlayer(
                data = media,
                modifier = modifier,
            )

        is UiMedia.Gif ->
            GifPlayer(
                data = media,
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
}
