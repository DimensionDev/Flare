package dev.dimension.flare.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.map
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.ListDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Presenter for editing list members.
 * This presenter should be used for managing list members.
 */
public class EditListMemberPresenter(
    private val accountType: AccountType,
    private val listId: String,
) : PresenterBase<EditListMemberState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): EditListMemberState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        var filter by remember { mutableStateOf("") }
        val userState =
            serviceState
                .flatMap { service ->
                    if (filter.isEmpty()) {
                        UiState.Error(EmptyQueryException)
                    } else {
                        remember(service, filter) {
                            require(service is ListDataSource)
                            combine(
                                service.searchUser(query = filter, scope = scope),
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

            override fun addMember(userKey: MicroBlogKey) {
                serviceState.onSuccess {
                    scope.launch {
                        require(it is ListDataSource)
                        it.addMember(listId, userKey)
                    }
                }
            }

            override fun removeMember(userKey: MicroBlogKey) {
                serviceState.onSuccess {
                    scope.launch {
                        require(it is ListDataSource)
                        it.removeMember(listId, userKey)
                    }
                }
            }
        }
    }
}

@Immutable
public interface EditListMemberState {
    /**
     * Users that can be added to the list.
     * This is a combination of search results and the current list members.
     * pair.first is the user, pair.second is true if the user is already a member of the list.
     */
    public val users: PagingState<Pair<UiUserV2, Boolean>>

    /**
     * Set the filter for searching users.
     */
    public fun setFilter(value: String)

    public fun addMember(userKey: MicroBlogKey)

    public fun removeMember(userKey: MicroBlogKey)
}

public data object EmptyQueryException : Exception("Empty query")
