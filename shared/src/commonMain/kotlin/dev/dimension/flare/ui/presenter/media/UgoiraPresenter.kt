package dev.dimension.flare.ui.presenter.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.repository.UgoiraRepository
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.media.UgoiraAnimation
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Owns playback preparation and retries; exports explicitly load and release their own lease. */
public class UgoiraPresenter(
    private val media: UiMedia.Ugoira,
) : PresenterBase<UgoiraPresenter.State>() {
    private val repository by koinInject<UgoiraRepository>()

    @Composable
    override fun body(): State =
        key(media) {
            var active by remember { mutableStateOf(false) }
            var attempt by remember { mutableIntStateOf(0) }
            var currentAnimation by remember { mutableStateOf<UgoiraAnimation?>(null) }
            var downloadProgress by remember { mutableFloatStateOf(0f) }
            var hasFailed by remember { mutableStateOf(false) }
            var failedAnimation by remember { mutableStateOf<UgoiraAnimation?>(null) }
            val lifecycle = remember { Mutex() }
            val isActive = active

            LaunchedEffect(isActive, attempt) {
                // Effect cancellation does not wait for cleanup; serialize release before the next load.
                lifecycle.withLock {
                    hasFailed = false
                    failedAnimation = null
                    downloadProgress = 0f
                    if (!isActive) return@withLock
                    try {
                        val loaded = repository.load(media) { downloadProgress = it }
                        currentAnimation = loaded
                        try {
                            awaitCancellation()
                        } finally {
                            currentAnimation = null
                            withContext(NonCancellable) {
                                try {
                                    if (failedAnimation === loaded) repository.invalidate(loaded)
                                } finally {
                                    repository.release(loaded).join()
                                }
                            }
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        hasFailed = true
                    }
                }
            }

            object : State {
                override val animation: UgoiraAnimation? = currentAnimation
                override val progress: Float = downloadProgress
                override val failed: Boolean = hasFailed

                override fun setActive(value: Boolean) {
                    active = value
                }

                override fun retry() {
                    if (hasFailed) attempt++
                }

                override fun onDecodeFailure(value: UgoiraAnimation) {
                    if (currentAnimation === value) {
                        failedAnimation = value
                        hasFailed = true
                    }
                }
            }
        }

    @Throws(Exception::class)
    public suspend fun load(onProgress: (Float) -> Unit): UgoiraAnimation = repository.load(media, onProgress)

    public fun release(animation: UgoiraAnimation) {
        repository.release(animation)
    }

    @Immutable
    public interface State {
        public val animation: UgoiraAnimation?
        public val progress: Float
        public val failed: Boolean

        public fun setActive(value: Boolean)

        public fun retry()

        public fun onDecodeFailure(value: UgoiraAnimation)
    }
}
