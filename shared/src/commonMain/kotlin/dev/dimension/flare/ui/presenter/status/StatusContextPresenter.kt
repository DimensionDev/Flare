package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.PresenterBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class StatusContextPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<StatusContextState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): StatusContextState {
        val scope = rememberCoroutineScope()
        val listState =
            accountServiceProvider(accountType = accountType, repository = accountRepository)
                .map { service ->
                    remember(service, statusKey) {
                        service.context(statusKey, scope = scope)
                    }.collectAsLazyPagingItems()
                }.toPagingState()
        return object : StatusContextState(
            listState,
        ) {
            override suspend fun refresh() {
                listState.onSuccess {
                    refreshSuspend()
                }
            }
        }
    }
}

abstract class StatusContextState(
    val listState: PagingState<UiTimeline>,
) {
    abstract suspend fun refresh()
}
