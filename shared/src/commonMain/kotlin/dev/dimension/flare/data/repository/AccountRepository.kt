package dev.dimension.flare.data.repository

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import dev.dimension.flare.common.Locale
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.data.database.app.model.DbGuestData
import dev.dimension.flare.data.datasource.guest.GuestMastodonDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiAccount.Companion.toUi
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

private const val DEFAULT_GUEST_HOST = "mastodon.social"
private val DEFAULT_GUEST_PLATFORM_TYPE = PlatformType.Mastodon

class AccountRepository(
    private val appDatabase: AppDatabase,
    private val coroutineScope: CoroutineScope,
) {
    val activeAccount: Flow<UiAccount?> by lazy {
        appDatabase.accountDao().activeAccount().map {
            it?.toUi()
        }
    }
    val allAccounts: Flow<ImmutableList<UiAccount>> by lazy {
        appDatabase.accountDao().allAccounts().map {
            it.map { it.toUi() }.toImmutableList()
        }
    }

    val guestData by lazy {
        appDatabase.guestDataDao().get().map {
            it ?: DbGuestData(host = DEFAULT_GUEST_HOST, platformType = DEFAULT_GUEST_PLATFORM_TYPE)
        }
    }

    fun setGuestData(
        host: String,
        platformType: PlatformType,
    ) = coroutineScope.launch {
        appDatabase.guestDataDao().insert(
            DbGuestData(
                host = host,
                platformType = platformType,
            ),
        )
    }

    fun addAccount(account: UiAccount) =
        coroutineScope.launch {
            appDatabase.accountDao().insert(
                DbAccount(
                    account_key = account.accountKey,
                    platform_type = account.platformType,
                    last_active = Clock.System.now().toEpochMilliseconds(),
                    credential_json = account.credential.encodeJson(),
                ),
            )
        }

    fun setActiveAccount(accountKey: MicroBlogKey) =
        coroutineScope.launch {
            appDatabase.accountDao().setLastActive(
                accountKey,
                Clock.System.now().toEpochMilliseconds(),
            )
        }

    fun delete(accountKey: MicroBlogKey) =
        coroutineScope.launch {
            appDatabase.accountDao().delete(accountKey)
        }

    fun getFlow(accountKey: MicroBlogKey): Flow<UiAccount?> =
        appDatabase.accountDao().get(accountKey).map {
            it?.toUi()
        }
}

data object NoActiveAccountException : Exception("No active account.")

data object LoginExpiredException : Exception("Login expired.")

@Composable
internal fun activeAccountPresenter(repository: AccountRepository): State<UiState<UiAccount>> =
    remember(repository) {
        repository.activeAccount
            .map<UiAccount?, UiState<UiAccount>> {
                if (it == null) {
                    UiState.Error(NoActiveAccountException)
                } else {
                    UiState.Success(it)
                }
            }
    }.collectAsState(initial = UiState.Loading())

@Composable
internal fun accountProvider(
    accountType: AccountType,
    repository: AccountRepository,
): State<UiState<UiAccount>> =
    produceState<UiState<UiAccount>>(
        initialValue = UiState.Loading(),
        key1 = accountType,
    ) {
        when (accountType) {
            AccountType.Active -> repository.activeAccount
            is AccountType.Specific -> repository.getFlow(accountKey = accountType.accountKey)
            AccountType.Guest -> flowOf(null)
        }.distinctUntilChanged()
            .map {
                if (it == null) {
                    UiState.Error(NoActiveAccountException)
                } else {
                    UiState.Success(it)
                }
            }.collect {
                value = it
            }
    }

@Composable
internal fun accountServiceProvider(
    accountType: AccountType,
    repository: AccountRepository,
): UiState<MicroblogDataSource> {
    if (accountType is AccountType.Guest) {
        val guestData by repository.guestData.collectAsUiState()
        return guestData.map {
            remember(it) {
                when (it.platformType) {
                    PlatformType.Mastodon ->
                        GuestMastodonDataSource(
                            host = it.host,
                            locale = Locale.language,
                        )
                    else -> throw UnsupportedOperationException()
                }
            }
        }
    }
    val account by accountProvider(accountType = accountType, repository = repository)
    return account.map {
        remember(it) {
            it.dataSource
        }
    }
}

@Composable
internal fun allAccountsPresenter(repository: AccountRepository): State<UiState<ImmutableList<UiAccount>>> =
    remember(repository) {
        repository.allAccounts
            .map {
                UiState.Success(it.toImmutableList())
            }
    }.collectAsState(initial = UiState.Loading())
