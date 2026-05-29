package dev.dimension.flare.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class AntennasListPresenter(
    private val accountType: AccountType,
) : PresenterBase<AntennasListPresenter.State>(),
    KoinComponent {
    private val accountService: AccountService by inject()

    private val serviceFlow by lazy {
        accountService.accountServiceFlow(accountType).map {
            require(it is MisskeyDataSource)
            it
        }
    }

    @androidx.compose.runtime.Immutable
    public interface State {
        public val data: PagingState<UiList.Antenna>

        public fun refresh()

        public suspend fun refreshSuspend()
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val service by serviceFlow.collectAsUiState()
        val data =
            service
                .map {
                    remember {
                        it.antennasList().cachedIn(scope)
                    }.collectAsLazyPagingItems()
                }.toPagingState()
        return object : State {
            override val data: PagingState<UiList.Antenna> = data

            override fun refresh() {
                scope.launch {
                    data.refreshSuspend()
                }
            }

            override suspend fun refreshSuspend() {
                data.refreshSuspend()
            }
        }
    }
}
