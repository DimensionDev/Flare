package dev.dimension.flare.data.repository

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import dev.dimension.flare.common.Locale
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.guest.mastodon.GuestMastodonDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiAccount.Companion.toUi
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Clock

internal class AccountRepository(
    private val appDatabase: AppDatabase,
    private val coroutineScope: CoroutineScope,
    internal val appDataStore: AppDataStore,
    private val cacheDatabase: CacheDatabase,
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
            cacheDatabase.pagingTimelineDao().deleteByAccountType(
                AccountType.Specific(accountKey),
            )
            cacheDatabase.statusDao().deleteByAccountType(
                AccountType.Specific(accountKey),
            )
            cacheDatabase.userDao().deleteHistoryByAccountType(
                AccountType.Specific(accountKey),
            )
            cacheDatabase.emojiDao().clearHistoryByAccountType(
                AccountType.Specific(accountKey),
            )
            cacheDatabase.messageDao().clearMessageTimeline(
                AccountType.Specific(accountKey),
            )
            appDatabase.accountDao().delete(accountKey)
        }

    fun getFlow(accountKey: MicroBlogKey): Flow<UiAccount?> =
        appDatabase.accountDao().get(accountKey).map {
            it?.toUi()
        }
}

public data object NoActiveAccountException : Exception("No active account.")

public data class LoginExpiredException(
    val accountKey: MicroBlogKey,
    val platformType: PlatformType,
) : Exception("Login expired.")

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
): UiState<MicroblogDataSource> =
    remember(
        accountType,
    ) {
        accountServiceFlow(
            accountType = accountType,
            repository = repository,
        )
    }.collectAsUiState().value

internal fun accountServiceFlow(
    accountType: AccountType,
    repository: AccountRepository,
): Flow<MicroblogDataSource> =
    when (accountType) {
        AccountType.Active -> {
            repository.activeAccount.map {
                it?.dataSource ?: throw NoActiveAccountException
            }
        }
        AccountType.Guest -> {
            val guestData = repository.appDataStore.guestDataStore.data
            guestData.map {
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
        is AccountType.Specific -> {
            repository
                .getFlow(accountType.accountKey)
                .map {
                    it?.dataSource ?: throw NoActiveAccountException
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
