package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.presenter.PresenterBase

public abstract class TimelinePresenter : PresenterBase<TimelineState>() {
    @Composable
    final override fun body(): TimelineState {
        val listState = listState()

        return object : TimelineState {
            override val listState = listState

            override suspend fun refresh() {
                listState.onSuccess {
                    refreshSuspend()
                }
            }
        }
    }

    @Composable
    internal abstract fun listState(): PagingState<UiTimeline>
}

@Immutable
public interface TimelineState {
    public val listState: PagingState<UiTimeline>

    public suspend fun refresh()
}
