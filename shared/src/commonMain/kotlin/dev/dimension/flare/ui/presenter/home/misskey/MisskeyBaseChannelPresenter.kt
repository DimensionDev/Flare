package dev.dimension.flare.ui.presenter.home.misskey

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public abstract class MisskeyBaseChannelPresenter(
    private val accountType: AccountType,
) : PresenterBase<MisskeyChannelsState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    internal abstract fun getPagingData(
        scope: CoroutineScope,
        serviceState: UiState<MicroblogDataSource>,
    ): PagingState<UiList>

    @Composable
    override fun body(): MisskeyChannelsState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val data = getPagingData(scope, serviceState)

        return object : MisskeyChannelsState {
            override val data = data

            override suspend fun refreshSuspend() {
                data.refreshSuspend()
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
