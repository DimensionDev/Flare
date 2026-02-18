package dev.dimension.flare.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.filter
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.list.ListDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Presenter for editing lists for a user.
 * This presenter should be used for managing lists for a user.
 */
public class EditAccountListPresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey,
) : PresenterBase<EditAccountListState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val userListFlow by lazy {
        accountServiceFlow(accountType, accountRepository)
            .flatMapLatest { service ->
                require(service is ListDataSource)
                service.listMemberHandler.userLists(userKey).toUi()
            }.map {
                it.map {
                    it.toImmutableList()
                }
            }
    }

    @Composable
    override fun body(): EditAccountListState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val allList =
            serviceState
                .map { service ->
                    require(service is ListDataSource)
                    remember(service) {
                        service.listHandler.data.cachedIn(scope).map {
                            it.filter {
                                !it.readonly
                            }
                        }
                    }.collectAsLazyPagingItems()
                }.toPagingState()
        val userLists by userListFlow.flattenUiState()
        return object : EditAccountListState {
            override val lists = allList
            override val userLists = userLists

            override fun addList(list: UiList) {
                serviceState.onSuccess {
                    require(it is ListDataSource)
                    scope.launch {
                        it.listMemberHandler.addMember(list.id, userKey = userKey)
                    }
                }
            }

            override fun removeList(list: UiList) {
                serviceState.onSuccess {
                    require(it is ListDataSource)
                    scope.launch {
                        it.listMemberHandler.removeMember(list.id, userKey = userKey)
                    }
                }
            }

            override fun isInList(list: UiList): UiState<Boolean> =
                userLists.map { item ->
                    item.any {
                        it.id == list.id
                    }
                }
        }
    }
}

@Immutable
public interface EditAccountListState {
    /**
     * All lists.
     */
    public val lists: PagingState<UiList>

    /**
     * Lists that the user is a member of.
     */
    public val userLists: UiState<ImmutableList<UiList>>

    public fun addList(list: UiList)

    public fun removeList(list: UiList)

    public fun isInList(list: UiList): UiState<Boolean>
}
