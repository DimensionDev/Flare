package dev.dimension.flare.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.ListDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Retrieving lists.
 * This presenter should be used for displaying lists.
 */
public class AllListPresenter(
    private val accountType: AccountType,
) : PresenterBase<AllListState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): AllListState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val items =
            serviceState
                .map { service ->
                    remember(service) {
                        require(service is ListDataSource)
                        service.myList(scope = scope)
                    }.collectAsLazyPagingItems()
                }.toPagingState()
        return object : AllListState {
            override val items = items

            override val isRefreshing = items.isRefreshing

            override fun refresh() {
                scope.launch {
                    items.refreshSuspend()
                }
            }

            override suspend fun refreshSuspend() {
                items.refreshSuspend()
            }
        }
    }
}

@Immutable
public interface AllListState {
    public val items: PagingState<UiList>
    public val isRefreshing: Boolean

    public fun refresh()

    public suspend fun refreshSuspend()
}
