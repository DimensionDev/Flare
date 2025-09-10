package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberPlayerState(uri: String): PlayerState {
    // TODO: implementation for iOS
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
