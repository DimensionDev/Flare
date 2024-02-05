package dev.dimension.flare.ui.component

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    uri: String,
    previewUri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    showControls: Boolean = false,
    keepScreenOn: Boolean = false,
    aspectRatio: Float? = null,
) {
    var isLoaded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        AndroidView(
            modifier =
                Modifier
                    .matchParentSize(),
            factory = { context ->
                val exoPlayer =
                    ExoPlayer.Builder(context)
                        .build()
                        .apply {
                            setMediaItem(MediaItem.fromUri(uri))
                            prepare()
                            playWhenReady = true
                            repeatMode = Player.REPEAT_MODE_ALL
                            volume = if (muted) 0f else 1f
                            addListener(
                                object : Player.Listener {
                                    override fun onIsLoadingChanged(isLoading: Boolean) {
                                        isLoaded = !isLoading || duration > 0
                                    }
                                },
                            )
                            if (aspectRatio == null) {
                                this.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                            }
                        }
                PlayerView(context).apply {
                    useController = showControls
                    player = exoPlayer
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    this.keepScreenOn = keepScreenOn
                }
            },
            onRelease = {
                it.player?.release()
            },
        )
        if (!isLoaded && previewUri != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                NetworkImage(
                    model = previewUri,
                    contentDescription = contentDescription,
                    modifier =
                        Modifier
                            .let {
                                if (aspectRatio != null) {
                                    it.aspectRatio(
                                        aspectRatio,
                                        matchHeightConstraintsFirst = aspectRatio > 1f,
                                    )
                                } else {
                                    it
                                }
                            },
                )
            }
            LinearProgressIndicator(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
            )
        }
    }
}
