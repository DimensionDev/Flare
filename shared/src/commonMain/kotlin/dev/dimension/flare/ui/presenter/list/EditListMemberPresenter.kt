package dev.dimension.flare.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.map
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.emptyFlow
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.datasource.ListDataSource
import dev.dimension.flare.data.datasource.microblog.paging.toPagingSource
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Immutable
public interface EditListMemberState {
    public val users: PagingState<Pair<UiProfile, Boolean>>

    public fun setFilter(value: String)

    public fun addMember(userKey: MicroBlogKey)

    public fun removeMember(userKey: MicroBlogKey)
}

public class EditListMemberPresenter(
    private val accountType: AccountType,
    private val listId: String,
) : PresenterBase<EditListMemberState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Composable
    override fun body(): EditListMemberState {
        val scope = rememberCoroutineScope()
        var filter by remember { mutableStateOf("") }
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)

        val membersFlow: Flow<List<UiProfile>> =
            remember(accountType, listId) {
                accountServiceFlow(accountType, accountRepository).flatMapLatest { service ->
                    if (service is ListDataSource) {
                        service.listMemberHandler.listMembersListFlow(listId)
                    } else {
                        flowOf(emptyList<UiProfile>())
                    }
                }
            }
        val members by membersFlow.collectAsState(initial = emptyList())

        val usersFlow: Flow<PagingData<Pair<UiProfile, Boolean>>> =
            remember(accountType, listId, filter, members) {
                accountServiceFlow(accountType, accountRepository).flatMapLatest { service ->
                    if (service !is ListDataSource || filter.isEmpty()) {
                        PagingData.emptyFlow<Pair<UiProfile, Boolean>>()
                    } else {
                        Pager(config = pagingConfig) {
                            service.searchUser(filter).toPagingSource()
                        }.flow.map { pagingData ->
                            pagingData.map { user ->
                                user to members.any { member -> member.key == user.key }
                            }
                        }
                    }
                }
            }
        val usersState =
            usersFlow
                .cachedIn(scope)
                .collectAsLazyPagingItems()
                .toPagingState()

        return object : EditListMemberState {
            override val users: PagingState<Pair<UiProfile, Boolean>> = usersState

            override fun setFilter(value: String) {
                filter = value
            }

            override fun addMember(userKey: MicroBlogKey) {
                serviceState.onSuccess { service ->
                    scope.launch {
                        if (service is ListDataSource) {
                            service.listMemberHandler.addMember(listId, userKey)
                        }
                    }
                }
            }

            override fun removeMember(userKey: MicroBlogKey) {
                serviceState.onSuccess { service ->
                    scope.launch {
                        if (service is ListDataSource) {
                            service.listMemberHandler.removeMember(listId, userKey)
                        }
                    }
                }
            }
        }
    }
}
