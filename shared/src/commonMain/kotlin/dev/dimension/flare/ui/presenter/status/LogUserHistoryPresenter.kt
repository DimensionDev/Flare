package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbUserHistory
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.activeAccountPresenter
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.datetime.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class LogUserHistoryPresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey,
) : PresenterBase<LogUserHistoryPresenter.State>(),
    KoinComponent {
    internal companion object {
        const val PAGING_KEY = "user_history"
    }

    private val accountRepository: AccountRepository by inject()
    private val cacheDatabase: CacheDatabase by inject()

    public interface State

    @Composable
    override fun body(): State {
        when (accountType) {
            is AccountType.Active ->
                activeAccountPresenter(accountRepository).value.map {
                    remember(it.accountKey) {
                        AccountType.Specific(it.accountKey) as DbAccountType
                    }
                }

            else ->
                remember(accountType) {
                    UiState.Success(accountType as DbAccountType)
                }
        }.onSuccess { accountType ->
            LaunchedEffect(Unit) {
                cacheDatabase.userDao().insertHistory(
                    DbUserHistory(
                        userKey = userKey,
                        accountType = accountType,
                        lastVisit = Clock.System.now().toEpochMilliseconds(),
                    ),
                )
            }
        }

        return object : State {
        }
    }
}
