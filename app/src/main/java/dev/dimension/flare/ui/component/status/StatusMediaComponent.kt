package dev.dimension.flare.ui.component.status

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.ui.component.AdaptiveGrid
import dev.dimension.flare.ui.component.AudioPlayer
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.VideoPlayer
import dev.dimension.flare.ui.humanizer.humanize
import dev.dimension.flare.ui.model.UiMedia
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlin.time.Duration.Companion.milliseconds

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
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
                        if (hideSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
}

@Composable
fun MediaItem(
    media: UiMedia,
    modifier: Modifier = Modifier,
    keepAspectRatio: Boolean = true,
    showCountdown: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val appearanceSettings = LocalAppearanceSettings.current
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
            val wifiState by wifiState()
            val shouldPlay =
                remember(appearanceSettings.videoAutoplay, wifiState) {
                    appearanceSettings.videoAutoplay == VideoAutoplay.ALWAYS ||
                        (appearanceSettings.videoAutoplay == VideoAutoplay.WIFI && wifiState)
                }
            if (shouldPlay) {
                VideoPlayer(
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
                        CircularProgressIndicator(
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
                                                shape = MaterialTheme.shapes.small,
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .align(Alignment.BottomStart),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text =
                                            remember(it) {
                                                it.milliseconds.humanize()
                                            },
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodySmall,
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
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                                .size(48.dp),
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
            VideoPlayer(
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
                CircularProgressIndicator(
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

@Composable
fun wifiState(): State<Boolean> {
    val context = LocalContext.current
    return produceState(initialValue = false) {
        context.observeWifiStateAsFlow().collect { value = it }
    }
}

fun Context.observeWifiStateAsFlow() =
    callbackFlow {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest =
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
        val networkCallback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    trySend(networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                }
            }
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }
