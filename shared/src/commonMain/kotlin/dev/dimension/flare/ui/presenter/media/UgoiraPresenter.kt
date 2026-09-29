package dev.dimension.flare.ui.presenter.media

import androidx.compose.runtime.Composable
import dev.dimension.flare.data.repository.UgoiraRepository
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.media.UgoiraAnimation
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.presenter.PresenterBase

/** Prepares one animation for native playback or export; release it when the consumer finishes. */
public class UgoiraPresenter(
    private val media: UiMedia.Ugoira,
) : PresenterBase<UgoiraPresenter.State>() {
    private val repository by koinInject<UgoiraRepository>()

    @Composable
    override fun body(): State =
        object : State {
            override suspend fun load(onProgress: (Float) -> Unit): UgoiraAnimation = this@UgoiraPresenter.load(onProgress)

            override suspend fun invalidate(animation: UgoiraAnimation): Unit = this@UgoiraPresenter.invalidate(animation)

            override fun release(animation: UgoiraAnimation): Unit = this@UgoiraPresenter.release(animation)
        }

    @Throws(Exception::class)
    public suspend fun load(onProgress: (Float) -> Unit): UgoiraAnimation = repository.load(media, onProgress)

    @Throws(Exception::class)
    public suspend fun invalidate(animation: UgoiraAnimation): Unit = repository.invalidate(animation)

    public fun release(animation: UgoiraAnimation): Unit = repository.release(animation)

    public interface State {
        @Throws(Exception::class)
        public suspend fun load(onProgress: (Float) -> Unit): UgoiraAnimation

        @Throws(Exception::class)
        public suspend fun invalidate(animation: UgoiraAnimation)

        public fun release(animation: UgoiraAnimation)
    }
}
