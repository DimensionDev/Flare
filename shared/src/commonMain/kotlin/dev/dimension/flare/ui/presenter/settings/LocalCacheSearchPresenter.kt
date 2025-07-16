package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.paging.Pager
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.filter
import androidx.paging.map
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.status.LogStatusHistoryPresenter
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class LocalCacheSearchPresenter :
    PresenterBase<LocalCacheSearchPresenter.State>(),
    KoinComponent {
    private val database: CacheDatabase by inject()
    private val accountRepository: AccountRepository by inject()

    public interface State {
        public val data: PagingState<UiTimeline>
        public val history: PagingState<UiTimeline>
        public val userHistory: PagingState<UiUserV2>
        public val searchUser: PagingState<UiUserV2>

        public fun setQuery(value: String)
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
                        config = pagingConfig,
                    ) {
                        database.pagingTimelineDao().searchHistoryPagingSource(query = "%$query%")
                    }.flow.let {
                        UiState.Success(it)
                    }
                }
            }
        val history =
            remember(allAccounts) {
                allAccounts.map { accounts ->
                    Pager(
                        config = pagingConfig,
                    ) {
                        database
                            .pagingTimelineDao()
                            .getStatusHistoryPagingSource(pagingKey = LogStatusHistoryPresenter.PAGING_KEY)
                    }.flow.map {
                        it.map {
                            val event =
                                if (it.status.status.data.accountType is AccountType.Specific) {
                                    accounts
                                        .firstOrNull { account -> account.accountKey == it.status.status.data.accountType.accountKey }
                                        ?.dataSource as? StatusEvent
                                } else {
                                    null
                                }
                            it.render(event)
                        }
                    }
                }
            }.map {
                it.collectAsLazyPagingItems()
            }.toPagingState()
        val data =
            remember(paging, allAccounts) {
                allAccounts.flatMap { accounts ->
                    paging.map { pagingData ->
                        pagingData.map {
                            it.map {
                                val event =
                                    if (it.status.data.accountType is AccountType.Specific) {
                                        accounts
                                            .firstOrNull { account -> account.accountKey == it.status.data.accountType.accountKey }
                                            ?.dataSource as? StatusEvent
                                    } else {
                                        null
                                    }
                                it.render(event)
                            }
                        }
                    }
                }
            }.map {
                it.collectAsLazyPagingItems()
            }.toPagingState()

        val userHistory =
            remember {
                Pager(
                    config = pagingConfig,
                ) {
                    database.userDao().getUserHistory()
                }.flow.map {
                    it
                        .filter {
                            it.data.accountType is AccountType.Specific
                        }.map {
                            require(it.data.accountType is AccountType.Specific)
                            it.user.render(it.data.accountType.accountKey) as UiUserV2
                        }
                }
            }.collectAsLazyPagingItems().toPagingState()

        val searchUser =
            remember(query, allAccounts) {
                if (query.isEmpty()) {
                    UiState.Error(Throwable("Query is empty"))
                } else {
                    allAccounts.map { accounts ->
                        Pager(
                            config = pagingConfig,
                        ) {
                            database.userDao().searchUser(query)
                        }.flow.map {
                            it.map { user ->
                                // TODO: potential bug: after logout, there might be no such a platform type for this user
                                val account =
                                    accounts.first { it.platformType == user.platformType }
                                user.render(account.accountKey) as UiUserV2
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
            override val history: PagingState<UiTimeline> = history
            override val userHistory: PagingState<UiUserV2> = userHistory
            override val searchUser: PagingState<UiUserV2> = searchUser
        }
    }
}
