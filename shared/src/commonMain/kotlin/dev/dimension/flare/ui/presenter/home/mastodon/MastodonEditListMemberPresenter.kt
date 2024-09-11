package dev.dimension.flare.ui.presenter.home.mastodon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.map
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.list.EditListMemberPresenter
import dev.dimension.flare.ui.presenter.list.EditListMemberState
import kotlinx.coroutines.flow.combine

class MastodonEditListMemberPresenter(
    private val accountType: AccountType,
    private val listId: String,
) : PresenterBase<MastodonEditListMemberState>() {
    @Composable
    override fun body(): MastodonEditListMemberState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType)
        var filter by remember { mutableStateOf("") }
        val userState =
            serviceState
                .flatMap { service ->
                    if (filter.isEmpty()) {
                        UiState.Error(EmptyQueryException)
                    } else {
                        remember(service, filter) {
                            require(service is MastodonDataSource)
                            combine(
                                service.searchFollowing(query = filter, scope = scope),
                                service.listMemberCache(listId),
                            ) { pagingData, cache ->
                                pagingData.map { user ->
                                    user to cache.any { it.key == user.key }
                                }
                            }
                        }.collectAsLazyPagingItems().let {
                            UiState.Success(it)
                        }
                    }
                }.toPagingState()
        val state =
            remember(
                accountType,
                listId,
            ) {
                EditListMemberPresenter(accountType, listId)
            }.body()
        return object : MastodonEditListMemberState, EditListMemberState by state {
            override val users = userState

            override fun setFilter(value: String) {
                filter = value
            }
        }
    }
}

interface MastodonEditListMemberState : EditListMemberState {
    val users: PagingState<Pair<UiUserV2, Boolean>>

    fun setFilter(value: String)
}

data object EmptyQueryException : Exception("Query is empty")
