package dev.dimension.flare.ui.presenter.home.misskey

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import dev.dimension.flare.di.koinInject

public class MisskeyChannelPresenter(
    private val accountType: AccountType,
    private val channelId: String,
) : PresenterBase<MisskeyChannelState>() {
    private val accountService: AccountService by koinInject()

    private val serviceFlow by lazy {
        accountService.accountServiceFlow(accountType).map {
            require(it is MisskeyDataSource)
            it
        }
    }

    private val timelinePresenter by lazy {
        object : TimelinePresenter() {
            override val loader: Flow<RemoteLoader<UiTimelineV2>> by lazy {
                serviceFlow.map { it.channelTimelineLoader(channelId) }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val infoFlow by lazy {
        serviceFlow
            .flatMapLatest {
                it.channelHandler.listInfo(channelId).toUi()
            }.map {
                it.mapNotNull { it }
            }
    }

    @Composable
    override fun body(): MisskeyChannelState {
        val scope = rememberCoroutineScope()
        val serviceState by serviceFlow.collectAsUiState()
        val timeline = timelinePresenter.body().listState
        val info by infoFlow.flattenUiState()
        val followed =
            serviceState
                .flatMap {
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
                        it.followChannel(list)
                    }
                }
            }

            override fun unfollow(list: UiList) {
                serviceState.onSuccess {
                    scope.launch {
                        it.unfollowChannel(list)
                    }
                }
            }

            override fun favorite(list: UiList) {
                serviceState.onSuccess {
                    scope.launch {
                        it.favoriteChannel(list)
                    }
                }
            }

            override fun unfavorite(list: UiList) {
                serviceState.onSuccess {
                    scope.launch {
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
    public val timeline: PagingState<UiTimelineV2>
    public val followed: UiState<Boolean>

    public suspend fun refreshSuspend()

    public fun follow(list: UiList)

    public fun unfollow(list: UiList)

    public fun favorite(list: UiList)

    public fun unfavorite(list: UiList)
}
