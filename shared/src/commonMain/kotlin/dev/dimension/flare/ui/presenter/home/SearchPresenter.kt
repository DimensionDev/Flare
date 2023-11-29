package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.common.LazyPagingItemsProxy
import dev.dimension.flare.common.collectPagingProxy
import dev.dimension.flare.data.repository.activeAccountServicePresenter
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.PresenterBase

class SearchPresenter : PresenterBase<SearchState>() {
    @Composable
    override fun body(): SearchState {
        val accountState = activeAccountServicePresenter()
        var query by remember { mutableStateOf("") }

        val user =
            accountState.map { (service, account) ->
                remember(account.accountKey, query) {
                    // TODO: Should we handle when query is empty?
                    service.searchUser(query)
                }.collectPagingProxy()
            }

        val status =
            accountState.map { (service, account) ->
                remember(account.accountKey, query) {
                    service.searchStatus(query)
                }.collectPagingProxy()
            }

        return object : SearchState {
            override val user = user
            override val status = status

            override fun search(new: String) {
                query = new
            }
        }
    }
}

interface SearchState {
    val user: UiState<LazyPagingItemsProxy<UiUser>>
    val status: UiState<LazyPagingItemsProxy<UiStatus>>

    fun search(new: String)
}
