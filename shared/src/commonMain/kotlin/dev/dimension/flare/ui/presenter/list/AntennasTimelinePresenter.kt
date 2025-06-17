package dev.dimension.flare.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class AntennasTimelinePresenter(
    private val accountType: AccountType,
    private val id: String,
) : TimelinePresenter(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    public interface State {
        public val data: PagingState<UiTimeline>
    }

    @Composable
    override fun listState(): PagingState<UiTimeline> {
        val scope = rememberCoroutineScope()
        val service = accountServiceProvider(accountType, accountRepository)
        return service
            .map {
                remember {
                    require(it is MisskeyDataSource)
                    it.antennasTimeline(id, scope)
                }.collectAsLazyPagingItems()
            }.toPagingState()
    }
}
