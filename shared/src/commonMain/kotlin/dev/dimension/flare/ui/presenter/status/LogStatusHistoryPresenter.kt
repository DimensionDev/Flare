package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.datetime.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class LogStatusHistoryPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<LogStatusHistoryPresenter.State>(),
    KoinComponent {
    internal companion object {
        const val PAGING_KEY = "status_history"
    }

    private val cacheDatabase: CacheDatabase by inject()

    public interface State

    @Composable
    override fun body(): State {
        LaunchedEffect(Unit) {
            cacheDatabase.pagingTimelineDao().insertAll(
                listOf(
                    DbPagingTimeline(
                        accountType = accountType,
                        statusKey = statusKey,
                        pagingKey = PAGING_KEY,
                        sortId = Clock.System.now().toEpochMilliseconds(),
                    ),
                ),
            )
        }

        return object : State {
        }
    }
}
