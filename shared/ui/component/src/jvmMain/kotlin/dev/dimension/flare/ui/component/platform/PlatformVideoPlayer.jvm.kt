package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import org.openani.mediamp.compose.rememberMediampPlayer
import org.openani.mediamp.playUri
import org.openani.mediamp.vlc.VlcMediampPlayer
import org.openani.mediamp.vlc.compose.VlcMediampPlayerSurface

@Composable
internal actual fun PlatformVideoPlayer(
    uri: String,
    previewUri: String?,
    contentDescription: String?,
    modifier: Modifier,
    muted: Boolean,
    showControls: Boolean,
    keepScreenOn: Boolean,
    aspectRatio: Float?,
    contentScale: ContentScale,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    autoPlay: Boolean,
    remainingTimeContent: @Composable (BoxScope.(Long) -> Unit)?,
    loadingPlaceholder: @Composable BoxScope.() -> Unit,
) {
    val player = rememberMediampPlayer() as VlcMediampPlayer
    LaunchedEffect(uri) { player.playUri(uri) }
//    LaunchedEffect(muted) {
//        player.player.audio().let {
//            if (muted) {
//                it.mute()
//            } else {
//                it.setVolume(100)
//            }
//        }
//    }
    VlcMediampPlayerSurface(
        mediampPlayer = player,
        modifier = modifier,
    )
}
