package dev.dimension.flare.ui.presenter.dm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.DirectMessageDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.PresenterBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class DMListPresenter(
    private val accountType: AccountType,
) : PresenterBase<DMListState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): DMListState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val items =
            serviceState
                .map { service ->
                    require(service is DirectMessageDataSource)
                    remember(service) {
                        service.directMessageList(scope = scope)
                    }.collectAsLazyPagingItems()
                }.toPagingState()
        return object : DMListState {
            override val items = items

            override val isRefreshing = false

            override suspend fun refreshSuspend() {
                items.onSuccess {
                    refreshSuspend()
                }
            }
        }
    }
}

@Immutable
public interface DMListState {
    public val items: PagingState<UiDMRoom>
    public val isRefreshing: Boolean

    public suspend fun refreshSuspend()
}
