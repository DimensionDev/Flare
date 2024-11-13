package dev.dimension.flare.ui.component

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CirclePlay
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.ui.humanizer.humanize
import dev.dimension.flare.ui.model.UiMedia
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlin.time.Duration.Companion.milliseconds

@Composable
actual fun VideoPlayer(
    data: UiMedia.Video,
    modifier: Modifier,
    keepAspectRatio: Boolean,
    contentScale: ContentScale,
    videoAutoplay: VideoAutoplay,
) {
    val wifiState by wifiState()
    val shouldPlay =
        remember(videoAutoplay, wifiState) {
            videoAutoplay == VideoAutoplay.ALWAYS ||
                (videoAutoplay == VideoAutoplay.WIFI && wifiState)
        }
    if (shouldPlay) {
        VideoPlayer(
            contentScale = contentScale,
            uri = data.url,
            muted = true,
            previewUri = data.thumbnailUrl,
            contentDescription = data.description,
            modifier =
                modifier
                    .fillMaxSize()
                    .let {
                        if (keepAspectRatio) {
                            it.aspectRatio(
                                data.aspectRatio,
                                matchHeightConstraintsFirst = data.aspectRatio > 1f,
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
                        model = data.thumbnailUrl,
                        contentDescription = data.description,
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
            remainingTimeContent = {
                Box(
                    modifier =
                        Modifier
                            .padding(16.dp)
                            .background(
                                Color.Black.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.small,
                            ).padding(horizontal = 8.dp, vertical = 4.dp)
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
            },
        )
    } else {
        Box(
            modifier = modifier,
        ) {
            NetworkImage(
                contentScale = contentScale,
                model = data.thumbnailUrl,
                contentDescription = data.description,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .let {
                            if (keepAspectRatio) {
                                it.aspectRatio(
                                    data.aspectRatio,
                                    matchHeightConstraintsFirst = data.aspectRatio > 1f,
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
            )
        }
    }
}

@Composable
actual fun GifPlayer(
    data: UiMedia.Gif,
    modifier: Modifier,
    keepAspectRatio: Boolean,
    contentScale: ContentScale,
) {
    VideoPlayer(
        contentScale = contentScale,
        uri = data.url,
        muted = true,
        previewUri = data.previewUrl,
        contentDescription = data.description,
        modifier =
            modifier
                .fillMaxSize()
                .let {
                    if (keepAspectRatio) {
                        it.aspectRatio(
                            data.aspectRatio,
                            matchHeightConstraintsFirst = data.aspectRatio > 1f,
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
                model = data.previewUrl,
                contentDescription = data.description,
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

@Composable
actual fun AudioPlayer(
    data: UiMedia.Audio,
    modifier: Modifier,
) {
    AudioPlayer(
        uri = data.url,
        previewUri = data.previewUrl,
        contentDescription = data.description,
        modifier = modifier,
    )
}

@Composable
private fun wifiState(): State<Boolean> {
    val context = LocalContext.current
    return produceState(initialValue = false) {
        context.observeWifiStateAsFlow().collect { value = it }
    }
}

private fun Context.observeWifiStateAsFlow() =
    callbackFlow {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest =
            NetworkRequest
                .Builder()
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
