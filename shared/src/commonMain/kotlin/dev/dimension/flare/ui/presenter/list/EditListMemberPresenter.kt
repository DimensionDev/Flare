package dev.dimension.flare.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.map
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.emptyFlow
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.list.ListDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Presenter for editing list members.
 * This presenter should be used for managing list members.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class EditListMemberPresenter(
    private val accountType: AccountType,
    private val listId: String,
) : PresenterBase<EditListMemberState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()
    private val filterFlow by lazy {
        MutableStateFlow("")
    }
    private val listMemberFlow by lazy {
        accountServiceFlow(accountType, accountRepository).flatMapLatest { service ->
            require(service is ListDataSource)
            service.listMemberHandler.listMembersListFlow(listId)
        }
    }
    private val userFlow by lazy {
        accountServiceFlow(accountType, accountRepository)
            .flatMapLatest { service ->
                listMemberFlow.flatMapLatest { members ->
                    filterFlow.flatMapLatest { filter ->
                        if (filter.isEmpty()) {
                            PagingData.emptyFlow()
                        } else {
                            require(service is ListDataSource)
                            service.searchUser(filter).map { users ->
                                users.map { user ->
                                    val isMember = members.any { it.key == user.key }
                                    Pair(user, isMember)
                                }
                            }
                        }
                    }
                }
            }
    }

    @Composable
    override fun body(): EditListMemberState {
        val scope = rememberCoroutineScope()
        val serviceState =
            accountServiceProvider(accountType = accountType, repository = accountRepository)
        val userState =
            remember {
                userFlow.cachedIn(scope)
            }.collectAsLazyPagingItems().toPagingState()
        return object : EditListMemberState {
            override val users = userState

            override fun setFilter(value: String) {
                filterFlow.value = value
            }

            override fun addMember(userKey: MicroBlogKey) {
                serviceState.onSuccess {
                    scope.launch {
                        require(it is ListDataSource)
                        it.listMemberHandler.addMember(listId, userKey)
                    }
                }
            }

            override fun removeMember(userKey: MicroBlogKey) {
                serviceState.onSuccess {
                    scope.launch {
                        require(it is ListDataSource)
                        it.listMemberHandler.removeMember(listId, userKey)
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
    public val users: PagingState<Pair<UiProfile, Boolean>>

    /**
     * Set the filter for searching users.
     */
    public fun setFilter(value: String)

    public fun addMember(userKey: MicroBlogKey)

    public fun removeMember(userKey: MicroBlogKey)
}
