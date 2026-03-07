package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbUserHistory
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.presenter.PresenterBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock

public class LogUserHistoryPresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey,
) : PresenterBase<LogUserHistoryPresenter.State>(),
    KoinComponent {
    internal companion object {
        const val PAGING_KEY = "user_history"
    }

    private val cacheDatabase: CacheDatabase by inject()

    @Immutable
    public interface State

    @Composable
    override fun body(): State {
        LaunchedEffect(Unit) {
            cacheDatabase.userDao().insertHistory(
                DbUserHistory(
                    userKey = userKey,
                    accountType = accountType as DbAccountType,
                    lastVisit = Clock.System.now().toEpochMilliseconds(),
                ),
            )
        }

        return object : State {
        }
    }
}
