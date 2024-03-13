package dev.dimension.flare.data.repository

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.model.AccountType
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import org.koin.compose.koinInject

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
            accountKey,
        )
    }

    fun delete(accountKey: MicroBlogKey) {
        appDatabase.dbAccountQueries.delete(accountKey)
    }

    fun get(accountKey: MicroBlogKey): UiAccount? {
        return appDatabase.dbAccountQueries.get(accountKey).executeAsOneOrNull()?.toUi()
    }

    fun getFlow(accountKey: MicroBlogKey): Flow<UiAccount?> {
        return appDatabase.dbAccountQueries.get(accountKey).asFlow().mapToOneOrNull(Dispatchers.IO).map {
            it?.toUi()
        }
    }
}

data object NoActiveAccountException : Exception("No active account.")

@Composable
internal fun activeAccountPresenter(repository: AccountRepository = koinInject()): State<UiState<UiAccount>> {
    return remember(repository) {
        repository.activeAccount
            .map<UiAccount?, UiState<UiAccount>> {
                if (it == null) {
                    UiState.Error(NoActiveAccountException)
                } else {
                    UiState.Success(it)
                }
            }
    }.collectAsState(initial = UiState.Loading())
}

@Composable
internal fun accountProvider(
    accountType: AccountType,
    repository: AccountRepository = koinInject(),
): State<UiState<UiAccount>> {
    return produceState<UiState<UiAccount>>(
        initialValue = UiState.Loading(),
        key1 = accountType,
    ) {
        when (accountType) {
            AccountType.Active -> repository.activeAccount
            is AccountType.Specific ->
                repository.getFlow(accountKey = accountType.accountKey)
        }.distinctUntilChanged().map {
            if (it == null) {
                UiState.Error(NoActiveAccountException)
            } else {
                UiState.Success(it)
            }
        }.collect {
            value = it
        }
    }
}

@Composable
internal fun accountServiceProvider(accountType: AccountType): UiState<MicroblogDataSource> {
    val account by accountProvider(accountType = accountType)
    return account.map {
        remember(it) {
            when (it) {
                is UiAccount.Mastodon -> {
                    it.dataSource
                }

                is UiAccount.Misskey -> {
                    it.dataSource
                }

                is UiAccount.Bluesky -> {
                    it.dataSource
                }

                is UiAccount.XQT -> {
                    it.dataSource
                }
            }
        }
    }
}

@Composable
internal fun allAccountsPresenter(repository: AccountRepository = koinInject()): State<UiState<ImmutableList<UiAccount>>> {
    return remember(repository) {
        repository.allAccounts
            .map {
                UiState.Success(it.toImmutableList())
            }
    }.collectAsState(initial = UiState.Loading())
}
