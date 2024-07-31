package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.presenter.PresenterBase

class SearchPresenter(
    private val accountType: AccountType,
    private val initialQuery: String = "",
) : PresenterBase<SearchState>() {
    @Composable
    override fun body(): SearchState {
        val scope = rememberCoroutineScope()
        val accountState = accountServiceProvider(accountType = accountType)
        var query by remember { mutableStateOf(initialQuery) }

        val user =
            accountState.flatMap { service ->
                if (query.isEmpty()) {
                    UiState.Error(IllegalStateException("Query is empty"))
                } else {
                    UiState.Success(
                        remember(service, query) {
                            service.searchUser(query, scope = scope)
                        }.collectAsLazyPagingItems(),
                    )
                }
            }

        val status =
            accountState.flatMap { service ->
                if (query.isEmpty()) {
                    UiState.Error(IllegalStateException("Query is empty"))
                } else {
                    UiState.Success(
                        remember(service, query) {
                            service.searchStatus(query, scope = scope)
                        }.collectAsLazyPagingItems(),
                    )
                }
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
    val users: UiState<LazyPagingItems<UiUserV2>>
    val status: UiState<LazyPagingItems<UiTimeline>>
    val searching: Boolean

    fun search(new: String)
}
