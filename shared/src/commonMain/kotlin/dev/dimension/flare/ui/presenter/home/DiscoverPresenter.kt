package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.common.LazyPagingItemsProxy
import dev.dimension.flare.common.collectPagingProxy
import dev.dimension.flare.data.repository.activeAccountServicePresenter
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.PresenterBase

class DiscoverPresenter : PresenterBase<DiscoverState>() {
    @Composable
    override fun body(): DiscoverState {
        val accountState = activeAccountServicePresenter()
        val users =
            accountState.map { (dataSource, account) ->
                remember(account.accountKey) {
                    dataSource.discoverUsers()
                }.collectPagingProxy()
            }
        val status =
            accountState.map { (dataSource, account) ->
                remember(account.accountKey) {
                    dataSource.discoverStatuses()
                }.collectPagingProxy()
            }
        val hashtags =
            accountState.map { (dataSource, account) ->
                remember(account.accountKey) {
                    dataSource.discoverHashtags()
                }.collectPagingProxy()
            }

        return object : DiscoverState {
            override val users = users
            override val status = status
            override val hashtags = hashtags
        }
    }
}

interface DiscoverState {
    val users: UiState<LazyPagingItemsProxy<UiUser>>
    val status: UiState<LazyPagingItemsProxy<UiStatus>>
    val hashtags: UiState<LazyPagingItemsProxy<UiHashtag>>
}
