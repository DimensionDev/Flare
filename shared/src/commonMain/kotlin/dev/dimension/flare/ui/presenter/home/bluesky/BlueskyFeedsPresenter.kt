package dev.dimension.flare.ui.presenter.home.bluesky

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BlueskyFeedsPresenter(
    private val accountType: AccountType,
) : PresenterBase<BlueskyFeedsState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): BlueskyFeedsState {
        val scope = rememberCoroutineScope()
        var query by remember { mutableStateOf<String?>(null) }
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val myFeeds =
            serviceState
                .map { service ->
                    require(service is BlueskyDataSource)
                    remember(service) {
                        service.myFeeds
                    }
                }.toPagingState()
        val popularFeeds =
            serviceState
                .map { service ->
                    require(service is BlueskyDataSource)
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

            override fun subscribe(list: UiList) {
                serviceState.onSuccess {
                    scope.launch {
                        require(it is BlueskyDataSource)
                        it.subscribeFeed(list)
                    }
                }
            }

            override fun unsubscribe(list: UiList) {
                serviceState.onSuccess {
                    scope.launch {
                        require(it is BlueskyDataSource)
                        it.unsubscribeFeed(list)
                    }
                }
            }
        }
    }
}

@Immutable
interface BlueskyFeedsState {
    val myFeeds: PagingState<UiList>
    val popularFeeds: PagingState<Pair<UiList, Boolean>>

    fun search(value: String)

    suspend fun refreshSuspend()

    fun subscribe(list: UiList)

    fun unsubscribe(list: UiList)
}
