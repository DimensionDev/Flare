package dev.dimension.flare.ui.presenter.home.misskey

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class MisskeyChannelListPresenter(
    private val accountType: AccountType,
) : PresenterBase<MisskeyChannelListPresenter.State>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    public interface State {
        public val type: Type
        public val data: PagingState<UiList>

        public suspend fun refreshSuspend()

        public fun refresh()

        public fun follow(list: UiList)

        public fun unfollow(list: UiList)

        public fun favorite(list: UiList)

        public fun unfavorite(list: UiList)

        public fun setType(data: Type)

        public val allTypes: ImmutableList<Type> get() = Type.entries.toImmutableList()

        public enum class Type {
            Following,
            Favorites,
            Owned,
            Featured,
        }
    }

    @Composable
    override fun body(): State {
        var type by remember { mutableStateOf(State.Type.Following) }
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val data =
            serviceState
                .map { service ->
                    require(service is MisskeyDataSource)
                    remember(type) {
                        when (type) {
                            State.Type.Following -> service.channelHandler.data.cachedIn(scope)
                            State.Type.Favorites -> service.myFavoriteChannelHandler.data.cachedIn(scope)
                            State.Type.Owned -> service.ownedChannelHandler.data.cachedIn(scope)
                            State.Type.Featured -> service.featuredChannels(scope)
                        }
                    }.collectAsLazyPagingItems()
                }.toPagingState()
        return object : State {
            override val data = data
            override val type = type

            override fun setType(data: State.Type) {
                type = data
            }

            override suspend fun refreshSuspend() {
                data.refreshSuspend()
            }

            override fun refresh() {
                scope.launch {
                    data.refreshSuspend()
                }
            }

            override fun follow(list: UiList) {
                serviceState.onSuccess {
                    scope.launch {
                        require(it is MisskeyDataSource)
                        it.followChannel(list)
                    }
                }
            }

            override fun unfollow(list: UiList) {
                serviceState.onSuccess {
                    scope.launch {
                        require(it is MisskeyDataSource)
                        it.unfollowChannel(list)
                    }
                }
            }

            override fun favorite(list: UiList) {
                serviceState.onSuccess {
                    scope.launch {
                        require(it is MisskeyDataSource)
                        it.favoriteChannel(list)
                    }
                }
            }

            override fun unfavorite(list: UiList) {
                serviceState.onSuccess {
                    scope.launch {
                        require(it is MisskeyDataSource)
                        it.unfavoriteChannel(list)
                    }
                }
            }
        }
    }
}
