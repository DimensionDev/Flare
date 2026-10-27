package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import dev.dimension.flare.ui.model.UiMedia

@Composable
internal actual fun rememberPlayerState(media: UiMedia): PlayerState {
    // TODO: implementation for JVM
    return object : PlayerState {
        override val playing: Boolean
            get() = false
        override val loading: Boolean
            get() = false
        override val duration: Long
            get() = 0L
        override val progress: Float
            get() = 0f

        override fun play() {
        }

        override fun pause() {
        }

        override fun seekTo(position: Long) {
        }
    }
}
