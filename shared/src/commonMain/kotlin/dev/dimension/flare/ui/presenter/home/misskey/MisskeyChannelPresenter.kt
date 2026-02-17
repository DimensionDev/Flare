package dev.dimension.flare.ui.presenter.home.misskey

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.mapNotNull
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class MisskeyChannelPresenter(
    private val accountType: AccountType,
    private val channelId: String,
) : PresenterBase<MisskeyChannelState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    private val timelinePresenter by lazy {
        object : TimelinePresenter() {
            override val loader by lazy {
                accountServiceFlow(accountType, accountRepository)
                    .map {
                        require(it is MisskeyDataSource)
                        it.channelTimelineLoader(channelId)
                    }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val infoFlow by lazy {
        accountServiceFlow(accountType, accountRepository)
            .flatMapLatest {
                require(it is MisskeyDataSource)
                it.channelHandler.listInfo(channelId).toUi()
            }.map {
                it.mapNotNull { it as? UiList.Channel }
            }
    }

    @Composable
    override fun body(): MisskeyChannelState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val timeline = timelinePresenter.body().listState
        val info by infoFlow.flattenUiState()
        val followed =
            serviceState
                .flatMap {
                    require(it is MisskeyDataSource)
                    remember(it) {
                        it.channelHandler.cacheData
                    }.collectAsUiState().value
                }.map {
                    it.any { it.id == channelId }
                }
        return object : MisskeyChannelState {
            override val info = info
            override val timeline = timeline
            override val followed = followed

            override suspend fun refreshSuspend() {
                timeline.refreshSuspend()
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

@Immutable
public interface MisskeyChannelState {
    public val info: UiState<UiList.Channel>
    public val timeline: PagingState<UiTimeline>
    public val followed: UiState<Boolean>

    public suspend fun refreshSuspend()

    public fun follow(list: UiList)

    public fun unfollow(list: UiList)

    public fun favorite(list: UiList)

    public fun unfavorite(list: UiList)
}
