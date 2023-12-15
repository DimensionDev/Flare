package dev.dimension.flare.ui.component.status

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.ui.component.AdaptiveGrid
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.model.UiMedia
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun StatusMediaComponent(
    data: ImmutableList<UiMedia>,
    onMediaClick: (UiMedia) -> Unit,
    sensitive: Boolean,
    modifier: Modifier = Modifier,
) {
    val appearanceSettings = LocalAppearanceSettings.current
    var hideSensitive by rememberSaveable {
        mutableStateOf(sensitive && !appearanceSettings.showSensitiveContent)
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
                                .clickable {
                                    onMediaClick(media)
                                },
                    )
                }
            },
            modifier =
                Modifier.let {
                    if (hideSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        it.blur(32.dp)
                    } else {
                        it
                    }
                },
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .let {
                        if (hideSensitive && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            it.background(MaterialTheme.colorScheme.surfaceContainer)
                        } else {
                            it
                        }
                    }
                    .let {
                        if (hideSensitive) {
                            it.clickable {
                                hideSensitive = false
                            }
                        } else {
                            it
                        }
                    }
                    .padding(16.dp),
        ) {
            AnimatedContent(
                hideSensitive,
                modifier =
                    Modifier
                        .matchParentSize(),
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
                                text = stringResource(R.string.status_sensitive_media),
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
                            Icon(
                                imageVector = Icons.Filled.VisibilityOff,
                                contentDescription = null,
                            )
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
) {
    Box(
        modifier =
            modifier
                .clipToBounds(),
    ) {
        when (media) {
            is UiMedia.Image -> {
                NetworkImage(
                    model = media.url,
                    contentDescription = media.description,
                    modifier =
                        Modifier.fillMaxSize()
                            .aspectRatio(media.aspectRatio, matchHeightConstraintsFirst = media.aspectRatio > 1f),
                )
            }

            is UiMedia.Video -> {
                NetworkImage(
                    model = media.thumbnailUrl,
                    contentDescription = media.description,
                    modifier =
                        Modifier.fillMaxSize()
                            .aspectRatio(media.aspectRatio, matchHeightConstraintsFirst = media.aspectRatio > 1f),
                )
            }

            is UiMedia.Audio -> Unit
            is UiMedia.Gif -> Unit
        }
    }
}
