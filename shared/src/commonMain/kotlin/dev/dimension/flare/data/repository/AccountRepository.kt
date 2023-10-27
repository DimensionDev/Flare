package dev.dimension.flare.data.repository

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.moriatsushi.koject.Provides
import com.moriatsushi.koject.Singleton
import com.moriatsushi.koject.compose.rememberInject
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.datasource.MicroblogDataSource
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiAccount.Companion.toUi
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.map
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

@Provides
@Singleton
class AccountRepository(
    private val appDatabase: AppDatabase,
) {
    val activeAccount: Flow<UiAccount?> by lazy {
        appDatabase.dbAccountQueries.activeAccount().asFlow().mapToOneOrNull(Dispatchers.IO).map {
            it?.toUi()
        }
    }
    val allAccounts: Flow<ImmutableList<UiAccount>> by lazy {
        appDatabase.dbAccountQueries.allAccounts().asFlow().mapToList(Dispatchers.IO).map {
            it.map { it.toUi() }.toImmutableList()
        }
    }

    fun addAccount(account: UiAccount) {
        appDatabase.dbAccountQueries.insert(
            accountKey = account.accountKey,
            platformType = account.platformType,
            lastActive = Clock.System.now().toEpochMilliseconds(),
            credentialJson = account.credential.encodeJson(),
        )
    }

    fun setActiveAccount(accountKey: MicroBlogKey) {
        appDatabase.dbAccountQueries.setLastActive(
            Clock.System.now().toEpochMilliseconds(),
            accountKey
        )
    }

    fun delete(accountKey: MicroBlogKey) {
        appDatabase.dbAccountQueries.delete(accountKey)
    }

    fun get(accountKey: MicroBlogKey): UiAccount? {
        return appDatabase.dbAccountQueries.get(accountKey).executeAsOneOrNull()?.toUi()
    }
}

object NoActiveAccountException : Exception("No active account.")

@Composable
fun activeAccountPresenter(
    repository: AccountRepository = rememberInject()
): State<UiState<UiAccount>> {
    return remember(repository) {
        repository.activeAccount
            .map {
                if (it == null) {
                    UiState.Error(NoActiveAccountException)
                } else {
                    UiState.Success(it)
                } as UiState<UiAccount>
            }
    }.collectAsState(initial = UiState.Loading())
}

@Composable
internal fun activeAccountServicePresenter(): UiState<Pair<MicroblogDataSource, UiAccount>> {
    val account by activeAccountPresenter()
    return account.map {
        accountServiceProvider(it) to it
    }
}

@Composable
fun accountServiceProvider(
    account: UiAccount,
): MicroblogDataSource {
    return remember(account.accountKey) {
        when (account) {
            is UiAccount.Mastodon -> {
                MastodonDataSource(
                    account = account,
                )
            }

            is UiAccount.Misskey -> {
                MisskeyDataSource(
                    account = account,
                )
            }

            is UiAccount.Bluesky -> {
                BlueskyDataSource(
                    account = account,
                )
            }
        }
    }
}

@Composable
internal fun allAccountsPresenter(
    repository: AccountRepository = rememberInject()
): State<UiState<ImmutableList<UiAccount>>> {
    return remember(repository) {
        repository.allAccounts
            .map {
                UiState.Success(it.toImmutableList())
            }
    }.collectAsState(initial = UiState.Loading())
}