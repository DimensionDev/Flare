package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.UIKitView
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.ui.model.UiMedia
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL
import platform.UIKit.UIView

@Composable
actual fun VideoPlayer(
    data: UiMedia.Video,
    modifier: Modifier,
    keepAspectRatio: Boolean,
    contentScale: ContentScale,
    videoAutoplay: VideoAutoplay,
) {
    val player = remember { AVPlayer(uRL = NSURL.URLWithString(data.url)!!) }
    val playbackLayer =
        remember {
            AVPlayerLayer().also {
                it.player = player
                it.videoGravity = platform.AVFoundation.AVLayerVideoGravityResizeAspect
            }
        }
    val avPlayerViewController =
        remember {
            AVPlayerViewController().also {
                it.player = player
                it.showsPlaybackControls = false
            }
        }
    UIKitView(
        factory = {
            val playerContainer = UIView()
            playerContainer.addSubview(avPlayerViewController.view)
            playerContainer
        },
        update = {
            avPlayerViewController.player?.play()
        },
        onRelease = {
            avPlayerViewController.player
        },
        modifier = modifier,
    )
}

@Composable
actual fun GifPlayer(
    data: UiMedia.Gif,
    modifier: Modifier,
    keepAspectRatio: Boolean,
    contentScale: ContentScale,
) {
    val player = remember { AVPlayer(uRL = NSURL.URLWithString(data.url)!!) }
    val playbackLayer =
        remember {
            AVPlayerLayer().also {
                it.player = player
                it.videoGravity = platform.AVFoundation.AVLayerVideoGravityResizeAspect
            }
        }
    val avPlayerViewController =
        remember {
            AVPlayerViewController().also {
                it.player = player
                it.showsPlaybackControls = false
            }
        }
    UIKitView(
        factory = {
            val playerContainer = UIView()
            playerContainer.addSubview(avPlayerViewController.view)
            playerContainer
        },
        update = {
            avPlayerViewController.player?.play()
        },
        onRelease = {
            avPlayerViewController.player
        },
        modifier = modifier,
    )
}

@Composable
actual fun AudioPlayer(
    data: UiMedia.Audio,
    modifier: Modifier,
) {
    val player = remember { AVPlayer(uRL = NSURL.URLWithString(data.url)!!) }
    val playbackLayer =
        remember {
            AVPlayerLayer().also {
                it.player = player
                it.videoGravity = platform.AVFoundation.AVLayerVideoGravityResizeAspect
            }
        }
    val avPlayerViewController =
        remember {
            AVPlayerViewController().also {
                it.player = player
                it.showsPlaybackControls = false
            }
        }
    UIKitView(
        factory = {
            val playerContainer = UIView()
            playerContainer.addSubview(avPlayerViewController.view)
            playerContainer
        },
        update = {
            avPlayerViewController.player?.play()
        },
        onRelease = {
            avPlayerViewController.player
        },
        modifier = modifier,
    )
}
