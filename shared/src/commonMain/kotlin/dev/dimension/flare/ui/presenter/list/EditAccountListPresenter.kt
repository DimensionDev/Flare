package dev.dimension.flare.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.ListDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
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

    @Composable
    override fun body(): EditAccountListState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val allList =
            serviceState
                .map { service ->
                    require(service is ListDataSource)
                    remember(service) {
                        service.myList(scope = scope)
                    }.collectAsLazyPagingItems()
                }.toPagingState()
        val userLists =
            serviceState.flatMap { service ->
                require(service is ListDataSource)
                remember(service) {
                    service.userLists(userKey)
                }.collectAsState().toUi()
            }

        return object : EditAccountListState {
            override val lists = allList
            override val userLists = userLists

            override fun addList(list: UiList) {
                serviceState.onSuccess {
                    require(it is ListDataSource)
                    scope.launch {
                        it.addMember(listId = list.id, userKey = userKey)
                    }
                }
            }

            override fun removeList(list: UiList) {
                serviceState.onSuccess {
                    require(it is ListDataSource)
                    scope.launch {
                        it.removeMember(listId = list.id, userKey = userKey)
                    }
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
}
