package dev.dimension.flare.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class AntennasListPresenter(
    private val accountType: AccountType,
) : PresenterBase<AntennasListPresenter.State>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    public interface State {
        public val data: PagingState<UiList>

        public fun refresh()
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val service = accountServiceProvider(accountType, accountRepository)
        val data =
            service
                .map {
                    remember {
                        require(it is MisskeyDataSource)
                        it.antennasList(scope)
                    }.collectAsLazyPagingItems()
                }.toPagingState()
        return object : State {
            override val data: PagingState<UiList> = data

            override fun refresh() {
                scope.launch {
                    data.refreshSuspend()
                }
            }
        }
    }
}
