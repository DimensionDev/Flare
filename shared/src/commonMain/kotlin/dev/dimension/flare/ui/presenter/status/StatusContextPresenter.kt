package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import dev.dimension.flare.ui.presenter.home.TimelineState
import dev.dimension.flare.ui.render.compareTo
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalCoroutinesApi::class)
public class StatusContextPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<StatusContextPresenter.State>(),
    KoinComponent {
    @Immutable
    public interface State : TimelineState {
        public val current: UiState<UiTimelineV2>
    }

    private val database: CacheDatabase by inject()
    private val accountRepository: AccountRepository by inject()

    private val currentStatusFlow by lazy {
        accountServiceFlow(
            accountType = accountType,
            repository = accountRepository,
        ).flatMapLatest { service ->
            if (service is PostDataSource) {
                service.postHandler.post(statusKey).toUi()
            } else {
                flowOf(null)
            }
        }.mapNotNull {
            it ?: UiState.Error(Exception("Current service does not support post data source"))
        }.distinctUntilChanged()
    }

    private val timelinePresenter by lazy {
        object : TimelinePresenter() {
            override val loader: Flow<RemoteLoader<UiTimelineV2>> by lazy {
                currentStatusFlow
                    .map { statusKey }
                    .distinctUntilChanged()
                    .flatMapLatest { key ->
                        accountServiceFlow(
                            accountType = accountType,
                            repository = accountRepository,
                        ).map { service ->
                            val loader = service.context(key)
                            if (loader is CacheableRemoteLoader<UiTimelineV2>) {
                                val pagingKey = loader.pagingKey
                                val exists = database.pagingTimelineDao().existsPaging(accountType as DbAccountType, pagingKey)
                                if (!exists) {
                                    val status = database.statusDao().get(statusKey, accountType).firstOrNull()
                                    status?.let {
                                        database.connect {
                                            database
                                                .pagingTimelineDao()
                                                .insertAll(
                                                    listOf(
                                                        DbPagingTimeline(
                                                            statusKey = statusKey,
                                                            pagingKey = pagingKey,
                                                            sortId = 0,
                                                        ),
                                                    ),
                                                )
                                        }
                                    }
                                }
                            }
                            loader
                        }
                    }
            }

            override suspend fun transform(data: UiTimelineV2): UiTimelineV2 {
                val currentCreatedAt = currentStatusFlow.firstOrNull()?.takeSuccess()?.createdAt
                if (data !is UiTimelineV2.Post || currentCreatedAt == null) {
                    return data
                }
                return if (data.createdAt <= currentCreatedAt) {
                    data.copy(
                        parents = persistentListOf(),
                    )
                } else {
                    data.copy(
                        parents =
                            data.parents
                                .filter {
                                    it.createdAt > currentCreatedAt
                                }.toPersistentList(),
                    )
                }
            }
        }
    }

    @Composable
    override fun body(): State {
        val current by currentStatusFlow.flattenUiState()
        val listState = timelinePresenter.body()
        current.onSuccess {
            remember {
                LogStatusHistoryPresenter(
                    accountType = accountType,
                    statusKey = statusKey,
                )
            }.body()
        }
        return object : State, TimelineState by listState {
            override val current = current
        }
    }
}
