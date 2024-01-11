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

class SearchPresenter(
    private val initialQuery: String = "",
) : PresenterBase<SearchState>() {
    @Composable
    override fun body(): SearchState {
        val accountState = activeAccountServicePresenter()
        var query by remember { mutableStateOf(initialQuery) }

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

        val isSearching = user is UiState.Success && status is UiState.Success && query.isNotEmpty()

        return object : SearchState {
            override val users = user
            override val status = status
            override val searching = isSearching

            override fun search(new: String) {
                query = new
            }
        }
    }
}

interface SearchState {
    val users: UiState<LazyPagingItemsProxy<UiUser>>
    val status: UiState<LazyPagingItemsProxy<UiStatus>>
    val searching: Boolean

    fun search(new: String)
}
