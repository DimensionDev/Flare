package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.map
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LocalHistoryPresenter :
    PresenterBase<LocalHistoryPresenter.State>(),
    KoinComponent {
    private val database: CacheDatabase by inject()
    private val accountRepository: AccountRepository by inject()

    interface State {
        val data: PagingState<UiTimeline>

        fun setQuery(value: String)
    }

    @Composable
    override fun body(): State {
        var query by remember { mutableStateOf("") }
        val allAccounts by accountRepository.allAccounts.collectAsUiState()
        val paging =
            remember(query) {
                if (query.isEmpty()) {
                    UiState.Error(Throwable("Query is empty"))
                } else {
                    Pager(
                        config = PagingConfig(pageSize = 20),
                    ) {
                        database.pagingTimelineDao().getHistoryPagingSource(query = query)
                    }.flow.let {
                        UiState.Success(it)
                    }
                }
            }
        val data =
            remember(paging, allAccounts) {
                allAccounts.flatMap { accounts ->
                    paging.map { pagingData ->
                        pagingData.map {
                            it.map {
                                val accountKey = it.timeline.accountKey
                                val event = accounts.first { it.accountKey == accountKey }.dataSource as StatusEvent
                                it.render(event)
                            }
                        }
                    }
                }
            }.map {
                it.collectAsLazyPagingItems()
            }.toPagingState()

        return object : State {
            override fun setQuery(value: String) {
                query = value
            }

            override val data: PagingState<UiTimeline> = data
        }
    }
}