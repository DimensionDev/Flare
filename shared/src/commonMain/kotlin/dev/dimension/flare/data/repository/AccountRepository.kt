package dev.dimension.flare.data.repository

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import dev.dimension.flare.common.Locale
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.takeSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.time.Clock

@Stable
@Single
internal class AccountRepository internal constructor(
    private val appDatabase: AppDatabase,
    private val coroutineScope: CoroutineScope,
    internal val appDataStore: AppDataStore,
    private val cacheDatabase: CacheDatabase,
    @Provided internal val platformRegistry: PlatformRegistry,
) {
    internal val activeAccount: Flow<UiState<UiAccount>> by lazy {
        appDatabase
            .accountDao()
            .activeAccount()
            .distinctUntilChangedBy {
                it?.account_key
            }.map {
                it?.toUi()
            }.map {
                if (it == null) {
                    UiState.Error(NoActiveAccountException)
                } else {
                    UiState.Success(it)
                }
            }
    }
    internal val allAccounts: Flow<ImmutableList<UiAccount>> by lazy {
        appDatabase.accountDao().sortedAccounts().map {
            it.map { it.toUi() }.toImmutableList()
        }
    }
    private val dataSourceCacheMutex = Mutex()
    private val dataSourceCache by lazy {
        mutableMapOf<MicroBlogKey, MicroblogDataSource>()
    }

    private val addAccountFlow by lazy {
        MutableStateFlow<UiAccount?>(null)
    }
    internal val onAdded: Flow<UiAccount> by lazy {
        addAccountFlow
            .mapNotNull { it }
            .distinctUntilChangedBy { it.accountKey }
    }
    private val removeAccountFlow by lazy {
        MutableStateFlow<MicroBlogKey?>(null)
    }
    internal val onRemoved: Flow<MicroBlogKey> by lazy {
        removeAccountFlow
            .mapNotNull { it }
            .distinctUntilChangedBy { it }
    }

    internal inline fun <reified T : Any> addAccount(
        account: UiAccount,
        credential: T,
    ) = addAccount(
        account = account,
        credential = credential,
        serializer = serializer(),
    )

    internal fun <T : Any> addAccount(
        account: UiAccount,
        credential: T,
        serializer: KSerializer<T>,
    ) = coroutineScope.launch {
        val existingAccount = appDatabase.accountDao().getAccount(account.accountKey)
        val credentialJson = credential.encodeJson(serializer)
        val dbAccount =
            existingAccount?.copy(
                credential_json = credentialJson,
                last_active = Clock.System.now().toEpochMilliseconds(),
            ) ?: DbAccount(
                account_key = account.accountKey,
                platform_type = account.platformType,
                credential_json = credentialJson,
                last_active = Clock.System.now().toEpochMilliseconds(),
                sort_id = appDatabase.accountDao().getMaxSortId()?.plus(1) ?: 0L,
            )
        appDatabase.accountDao().insert(dbAccount)
        addAccountFlow.value = account
    }

    internal inline fun <reified T : Any> updateCredential(
        accountKey: MicroBlogKey,
        credential: T,
    ) = updateCredential(
        accountKey = accountKey,
        credential = credential,
        serializer = serializer(),
    )

    internal fun <T : Any> updateCredential(
        accountKey: MicroBlogKey,
        credential: T,
        serializer: KSerializer<T>,
    ) = coroutineScope.launch {
        appDatabase.accountDao().setCredential(
            accountKey,
            credential.encodeJson(serializer),
        )
    }

    internal fun updateAccountOrder(accounts: List<MicroBlogKey>) =
        coroutineScope.launch {
            appDatabase.connect {
                accounts.forEachIndexed { index, accountKey ->
                    appDatabase.accountDao().setSortId(
                        accountKey,
                        index.toLong(),
                    )
                }
            }
        }

    internal fun setActiveAccount(accountKey: MicroBlogKey) =
        coroutineScope.launch {
            appDatabase.accountDao().setLastActive(
                accountKey,
                Clock.System.now().toEpochMilliseconds(),
            )
        }

    internal fun delete(accountKey: MicroBlogKey) =
        coroutineScope.launch {
            appDataStore.composeConfigData.updateData {
                it.copy(
                    lastAccounts = it.lastAccounts.filterNot { key -> key == accountKey },
                )
            }
            removeAccountFlow.value = accountKey
            dataSourceCacheMutex.withLock {
                val datasource = dataSourceCache.remove(accountKey)
                if (datasource is AutoCloseable) {
                    datasource.close()
                }
            }
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

    internal fun getFlow(accountKey: MicroBlogKey): Flow<UiState<UiAccount>> =
        appDatabase.accountDao().get(accountKey).map {
            if (it == null) {
                UiState.Error(NoActiveAccountException)
            } else {
                UiState.Success(it.toUi())
            }
        }

    internal suspend fun find(accountKey: MicroBlogKey): UiAccount? =
        appDatabase
            .accountDao()
            .get(accountKey)
            .firstOrNull()
            ?.toUi()

    internal inline fun <reified T : Any> credentialFlow(accountKey: MicroBlogKey): Flow<T> =
        credentialFlow(
            accountKey = accountKey,
            serializer = serializer(),
        )

    internal fun <T : Any> credentialFlow(
        accountKey: MicroBlogKey,
        serializer: KSerializer<T>,
    ): Flow<T> =
        appDatabase
            .accountDao()
            .get(accountKey)
            .mapNotNull { it }
            .map {
                it.credential_json.decodeJson(serializer)
            }

    internal suspend fun getOrCreateDataSource(account: UiAccount): MicroblogDataSource {
        val existingDataSource =
            dataSourceCacheMutex.withLock {
                dataSourceCache[account.accountKey]
            }
        if (existingDataSource != null) {
            return existingDataSource
        }

        val dbAccount =
            appDatabase.accountDao().getAccount(account.accountKey)
                ?: throw NoSuchElementException("Account not found: ${account.accountKey}")
        val dataSource =
            platformRegistry
                .require(account.platformType)
                .createDataSource(
                    RepositoryPlatformDataSourceContext(
                        accountKey = account.accountKey,
                        credentialJson = dbAccount.credential_json,
                    ),
                )

        val cached =
            dataSourceCacheMutex.withLock {
                dataSourceCache[account.accountKey]?.let {
                    it
                } ?: dataSource.also {
                    dataSourceCache[account.accountKey] = it
                }
            }
        if (cached !== dataSource && dataSource is AutoCloseable) {
            dataSource.close()
        }
        return cached
    }

    private inner class RepositoryPlatformDataSourceContext(
        override val accountKey: MicroBlogKey,
        private val credentialJson: String,
    ) : PlatformDataSourceContext {
        override fun <T : Any> credential(serializer: KSerializer<T>): T = credentialJson.decodeJson(serializer)

        override fun <T : Any> credentialFlow(serializer: KSerializer<T>): Flow<T> =
            this@AccountRepository.credentialFlow(
                accountKey = accountKey,
                serializer = serializer,
            )

        override suspend fun <T : Any> updateCredential(
            serializer: KSerializer<T>,
            credential: T,
        ) {
            appDatabase.accountDao().setCredential(
                accountKey,
                credential.encodeJson(serializer),
            )
        }
    }
}

private fun DbAccount.toUi(): UiAccount =
    UiAccount(
        accountKey = account_key,
        platformType = platform_type,
    )

public data object NoActiveAccountException : Exception("No active account.")

@Immutable
public data class LoginExpiredException(
    val accountKey: MicroBlogKey,
    val platformType: PlatformType,
) : Exception("Login expired.")

@Immutable
public data class RequireReLoginException(
    val accountKey: MicroBlogKey,
    val platformType: PlatformType,
) : Exception("Login required.")

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
            AccountType.Guest,
            is AccountType.GuestHost,
            -> {
                flowOf(
                    UiState.Error(
                        NoActiveAccountException,
                    ),
                )
            }

            is AccountType.Specific -> {
                repository.getFlow(accountType.accountKey)
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

@OptIn(ExperimentalCoroutinesApi::class)
internal fun accountServiceFlow(
    accountType: AccountType,
    repository: AccountRepository,
): Flow<MicroblogDataSource> =
    when (accountType) {
        AccountType.Guest -> {
            flowOf(
                repository.platformRegistry.require(PlatformType.Mastodon).guestDataSource(
                    host = "mastodon.social",
                    locale = Locale.language,
                ),
            )
        }

        is AccountType.GuestHost -> {
            flowOf(
                repository.platformRegistry.require(PlatformType.Mastodon).guestDataSource(
                    host = accountType.host,
                    locale = Locale.language,
                ),
            )
        }

        is AccountType.Specific -> {
            repository
                .getFlow(accountType.accountKey)
                .mapNotNull { it.takeSuccess() }
                .distinctUntilChangedBy { it.accountKey }
                .map { account -> repository.getOrCreateDataSource(account) }
        }
    }

internal fun activeAccountFlow(repository: AccountRepository): Flow<UiAccount?> =
    repository
        .activeAccount
        .map { it.takeSuccess() }
        .distinctUntilChangedBy { it?.accountKey }

internal fun allAccountServicesFlow(repository: AccountRepository): Flow<ImmutableList<MicroblogDataSource>> =
    repository.allAccounts.map { accounts ->
        accounts
            .map {
                repository.getOrCreateDataSource(it)
            }.toImmutableList()
    }
