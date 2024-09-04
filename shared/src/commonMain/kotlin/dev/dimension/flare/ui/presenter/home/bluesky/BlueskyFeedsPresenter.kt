package dev.dimension.flare.ui.presenter.home.bluesky

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.PresenterBase

class BlueskyFeedsPresenter(
    private val accountType: AccountType,
) : PresenterBase<BlueskyFeedsState>() {
    @Composable
    override fun body(): BlueskyFeedsState {
        var query by remember { mutableStateOf<String?>(null) }
        val serviceState = accountServiceProvider(accountType = accountType)
        val myFeeds = serviceState.map { service ->
            require(service is BlueskyDataSource)
            remember(service) {
                service.myFeeds
            }.collectAsState()
        }.toPagingState()
        val popularFeeds = serviceState.map { service ->
            require(service is BlueskyDataSource)
            remember(service, query) {
                service.popularFeeds(query = query)
            }.collectAsLazyPagingItems()
        }.toPagingState()

        return object : BlueskyFeedsState {
            override val myFeeds = myFeeds
            override val popularFeeds = popularFeeds
            override fun search(value: String) {
                query = value
            }
        }
    }
}

interface BlueskyFeedsState {
    val myFeeds: PagingState<UiList>
    val popularFeeds: PagingState<UiList>
    fun search(value: String)
}