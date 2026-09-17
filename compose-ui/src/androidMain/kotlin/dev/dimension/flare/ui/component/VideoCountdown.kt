package dev.dimension.flare.ui.component

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval

@OptIn(UnstableApi::class)
@Composable
internal fun BoxScope.VideoCountdown(
    player: Player,
    content: @Composable BoxScope.(Long) -> Unit,
) {
    // Read progress in this restart scope so a tick cannot recompose the player/surface.
    val progress = rememberProgressStateWithTickInterval(player, tickIntervalMs = 1_000L)
    val remainingTime =
        if (progress.durationMs == C.TIME_UNSET) {
            0L
        } else {
            (progress.durationMs - progress.currentPositionMs).coerceAtLeast(0L)
        }
    content(remainingTime)
}
