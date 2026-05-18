package dev.dimension.flare.data.repository

import androidx.compose.runtime.Stable
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
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.SocialPlatformRegistry
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.toUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

@Stable
public class AccountRepository(
    @PublishedApi internal val appDatabase: AppDatabase,
    private val coroutineScope: CoroutineScope,
    private val appDataStore: AppDataStore,
    private val cacheDatabase: CacheDatabase,
    private val platformRegistry: SocialPlatformRegistry,
) {
    public val activeAccount: Flow<UiState<UiAccount>> by lazy {
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
    public val allAccounts: Flow<ImmutableList<UiAccount>> by lazy {
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
    public val onAdded: Flow<UiAccount> by lazy {
        addAccountFlow
            .mapNotNull { it }
            .distinctUntilChangedBy { it.accountKey }
    }
    private val removeAccountFlow by lazy {
        MutableStateFlow<MicroBlogKey?>(null)
    }
    public val onRemoved: Flow<MicroBlogKey> by lazy {
        removeAccountFlow
            .mapNotNull { it }
            .distinctUntilChangedBy { it }
    }

    public fun addAccount(
        account: UiAccount,
        credential: UiAccount.Credential,
    ): Job = coroutineScope.launch {
        val existingAccount = appDatabase.accountDao().getAccount(account.accountKey)
        val dbAccount =
            existingAccount?.copy(
                credential_json = credential.encodeJson(),
                last_active = Clock.System.now().toEpochMilliseconds(),
            ) ?: DbAccount(
                account_key = account.accountKey,
                platform_type = account.platformType,
                credential_json = credential.encodeJson(),
                last_active = Clock.System.now().toEpochMilliseconds(),
                sort_id = appDatabase.accountDao().getMaxSortId()?.plus(1) ?: 0L,
            )
        appDatabase.accountDao().insert(dbAccount)
        addAccountFlow.value = account
    }

    public fun updateCredential(
        accountKey: MicroBlogKey,
        credential: UiAccount.Credential,
    ): Job = coroutineScope.launch {
        appDatabase.accountDao().setCredential(
            accountKey,
            credential.encodeJson(),
        )
    }

    public fun updateAccountOrder(accounts: List<MicroBlogKey>): Job =
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

    public fun setActiveAccount(accountKey: MicroBlogKey): Job =
        coroutineScope.launch {
            appDatabase.accountDao().setLastActive(
                accountKey,
                Clock.System.now().toEpochMilliseconds(),
            )
        }

    public fun delete(accountKey: MicroBlogKey): Job =
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

    public fun getFlow(accountKey: MicroBlogKey): Flow<UiState<UiAccount>> =
        appDatabase.accountDao().get(accountKey).map {
            if (it == null) {
                UiState.Error(NoActiveAccountException)
            } else {
                UiState.Success(it.toUi())
            }
        }

    public suspend fun find(accountKey: MicroBlogKey): UiAccount? =
        appDatabase
            .accountDao()
            .get(accountKey)
            .firstOrNull()
            ?.toUi()

    public inline fun <reified T : UiAccount.Credential> credentialFlow(accountKey: MicroBlogKey): Flow<T> =
        appDatabase
            .accountDao()
            .get(accountKey)
            .mapNotNull { it }
            .map {
                it.credential_json.decodeJson<T>()
            }

    public suspend fun getOrCreateDataSource(account: UiAccount): MicroblogDataSource =
        dataSourceCacheMutex.withLock {
            dataSourceCache.getOrPut(account.accountKey) {
                platformRegistry.createDataSource(account)
            }
        }

    public fun guestDataSource(
        type: PlatformType,
        host: String,
        locale: String,
    ): MicroblogDataSource =
        platformRegistry.guestDataSource(
            type = type,
            host = host,
            locale = locale,
        )
}
