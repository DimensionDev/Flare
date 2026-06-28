package dev.dimension.flare.ui.presenter.home.bluesky

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

public class BlueskyFeedsPresenter(
    private val accountType: AccountType,
) : PresenterBase<BlueskyFeedsState>() {
    private val accountService: AccountService by koinInject()

    private val serviceFlow by lazy {
        accountService.accountServiceFlow(accountType).map {
            require(it is BlueskyDataSource)
            it
        }
    }

    @Composable
    override fun body(): BlueskyFeedsState {
        val scope = rememberCoroutineScope()
        var query by remember { mutableStateOf<String?>(null) }
        val serviceState by serviceFlow.collectAsUiState()
        val myFeeds =
            serviceState
                .map { service ->
                    val flow =
                        remember(service) {
                            service.feedHandler.data.cachedIn(scope)
                        }
                    flow.collectAsLazyPagingItems()
                }.toPagingState()
        val popularFeeds =
            serviceState
                .map { service ->
                    remember(service, query) {
                        service.popularFeeds(query = query, scope = scope)
                    }.collectAsLazyPagingItems()
                }.toPagingState()

        return object : BlueskyFeedsState {
            override val myFeeds = myFeeds
            override val popularFeeds = popularFeeds

            override fun search(value: String) {
                query = value
            }

            override suspend fun refreshSuspend() {
                myFeeds.refreshSuspend()
                popularFeeds.refreshSuspend()
            }

            override fun subscribe(list: UiList.Feed) {
                serviceState.onSuccess {
                    scope.launch {
                        it.subscribeFeed(list)
                    }
                }
            }

            override fun unsubscribe(list: UiList.Feed) {
                serviceState.onSuccess {
                    scope.launch {
                        it.unsubscribeFeed(list)
                    }
                }
            }
        }
    }
}

@Immutable
public interface BlueskyFeedsState {
    public val myFeeds: PagingState<UiList.Feed>
    public val popularFeeds: PagingState<Pair<UiList.Feed, Boolean>>

    public fun search(value: String)

    public suspend fun refreshSuspend()

    public fun subscribe(list: UiList.Feed)

    public fun unsubscribe(list: UiList.Feed)
}
