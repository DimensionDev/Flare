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
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class EditListMemberPresenter(
    private val accountType: AccountType,
    private val listId: String,
) : PresenterBase<EditListMemberState>() {
    @Composable
    override fun body(): EditListMemberState {
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
        return object : EditListMemberState {
            override val users = userState

            override fun setFilter(value: String) {
                filter = value
            }

            override fun addMember(userId: String) {
                serviceState.onSuccess {
                    scope.launch {
                        require(it is MastodonDataSource)
                        it.addMember(listId, userId)
                    }
                }
            }

            override fun removeMember(userId: String) {
                serviceState.onSuccess {
                    scope.launch {
                        require(it is MastodonDataSource)
                        it.removeMember(listId, userId)
                    }
                }
            }
        }
    }
}

interface EditListMemberState {
    val users: PagingState<Pair<UiUserV2, Boolean>>

    fun setFilter(value: String)

    fun addMember(userId: String)

    fun removeMember(userId: String)
}

data object EmptyQueryException : Exception("Query is empty")
