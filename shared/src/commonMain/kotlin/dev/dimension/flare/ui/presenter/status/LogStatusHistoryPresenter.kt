package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.datetime.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue
import kotlin.uuid.Uuid

class LogStatusHistoryPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<LogStatusHistoryPresenter.State>(),
    KoinComponent {
    companion object {
        const val PAGING_KEY = "status_history"
    }

    private val accountRepository: AccountRepository by inject()
    private val cacheDatabase: CacheDatabase by inject()

    interface State

    @Composable
    override fun body(): State {
        val accountState by accountProvider(
            accountType = accountType,
            repository = accountRepository,
        )
        accountState.onSuccess {
            LaunchedEffect(Unit) {
                cacheDatabase.pagingTimelineDao().insertAll(
                    listOf(
                        DbPagingTimeline(
                            _id = Uuid.random().toString(),
                            accountKey = it.accountKey,
                            statusKey = statusKey,
                            pagingKey = PAGING_KEY,
                            sortId = Clock.System.now().toEpochMilliseconds(),
                        ),
                    ),
                )
            }
        }

        return object : State {
        }
    }
}
