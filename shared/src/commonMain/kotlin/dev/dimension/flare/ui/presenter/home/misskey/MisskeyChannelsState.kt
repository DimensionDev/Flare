package dev.dimension.flare.ui.presenter.home.misskey

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.ui.model.UiList

@Immutable
public interface MisskeyChannelsState {
    public val data: PagingState<UiList>

    public suspend fun refreshSuspend()

    public fun follow(list: UiList)

    public fun unfollow(list: UiList)

    public fun favorite(list: UiList)

    public fun unfavorite(list: UiList)
}
