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
import dev.dimension.flare.data.database.cache.clearHomeTimelinesForAccount
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.UnavailableMicroblogDataSource
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformCapability
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.UnsupportedPlatformException
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.takeSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC
import kotlin.time.Clock

internal sealed interface AccountChange {
    data class Added(
        val account: UiAccount,
    ) : AccountChange

    data class Removed(
        val accountKey: MicroBlogKey,
    ) : AccountChange
}

@Stable
@Single
internal class AccountRepository internal constructor(
    private val appDatabase: AppDatabase,
    private val coroutineScope: CoroutineScope,
    internal val appDataStore: AppDataStore,
    private val cacheDatabase: CacheDatabase,
    private val proxyRepository: ProxyRepository,
    @Provided internal val platformRegistry: PlatformRegistry,
    private val draftRepository: DraftRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    internal val activeAccount: Flow<UiState<UiAccount>> by lazy {
        platformRegistry.platforms.flatMapLatest { platforms ->
            combine(
                appDatabase.accountDao().activeAccount(platforms.map { it.platformId }),
                appDatabase.accountDao().allAccounts(),
            ) { active, accounts ->
                when {
                    active != null -> {
                        UiState.Success(active.toUi(platformRegistry))
                    }

                    accounts.isNotEmpty() -> {
                        UiState.Error(
                            NoAvailableAccountException(accounts.map { it.platformId }.distinct()),
                        )
                    }

                    else -> {
                        UiState.Error(NoActiveAccountException)
                    }
                }
            }.distinctUntilChangedBy { state ->
                when (state) {
                    is UiState.Success -> state.data.accountKey
                    is UiState.Error -> state.throwable
                    is UiState.Loading -> state
                }
            }
        }
    }
    internal val allAccounts: Flow<ImmutableList<UiAccount>> by lazy {
        appDatabase
            .accountDao()
            .sortedAccounts()
            .combine(platformRegistry.platforms) { accounts, _ ->
                accounts.map { it.toUi(platformRegistry) }.toImmutableList()
            }.distinctUntilChanged()
    }
    private val dataSourceCacheMutex = Mutex()
    internal val dataSourceRevisions = MutableStateFlow<Map<MicroBlogKey, Long>>(emptyMap())
    private val dataSourceCache by lazy {
        mutableMapOf<MicroBlogKey, Pair<PlatformSpec, MicroblogDataSource>>()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    internal val allAccountServices: Flow<ImmutableList<AccountMicroblogDataSource>> by lazy {
        combine(allAccounts, platformRegistry.platforms, dataSourceRevisions) { accounts, _, _ -> accounts }
            .mapLatest { accounts ->
                accounts
                    .filter { it.platformAvailable }
                    .map { account ->
                        AccountMicroblogDataSource(account.accountKey, account.platformId, getOrCreateDataSource(account))
                    }.toImmutableList()
            }.distinctUntilChanged()
    }

    private val accountChangeChannel = Channel<AccountChange>(Channel.UNLIMITED)
    internal val accountChanges: Flow<AccountChange> = accountChangeChannel.receiveAsFlow()

    internal val accountMutationMutex = Mutex()
    private val pendingDeletions =
        coroutineScope.async {
            val removedAccounts =
                try {
                    proxyRepository.settings.first().removedAccounts
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    error.printStackTrace()
                    return@async
                }
            for (key in removedAccounts) {
                try {
                    accountMutationMutex.withLock { deleteAccount(MicroBlogKey.valueOf(key)) }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    // Keep the tombstone so a later startup or login can retry cleanup.
                    error.printStackTrace()
                }
            }
        }

    internal suspend fun <T> withAccountChanges(block: suspend () -> T): T {
        pendingDeletions.await()
        return accountMutationMutex.withLock { block() }
    }

    internal suspend fun <T> restoreAccounts(
        keys: List<MicroBlogKey>,
        block: suspend () -> T,
    ): T =
        withAccountChanges {
            restoreAccountsLocked(keys, block)
        }

    private suspend fun <T> restoreAccountsLocked(
        keys: List<MicroBlogKey>,
        block: suspend () -> T,
    ): T {
        val removed = if (keys.isEmpty()) emptySet() else proxyRepository.settings.first().removedAccounts
        keys.distinct().filter { it.toString() in removed }.forEach { deleteAccount(it) }
        // A failed settings write rolls back the DB; cancellation cannot split restoration from commit.
        return withContext(NonCancellable) {
            appDatabase.connect {
                block().also { if (keys.isNotEmpty()) proxyRepository.restoreAccounts(keys) }
            }
        }
    }

    internal suspend inline fun <reified T : Any> addAccount(
        account: UiAccount,
        credential: T,
    ) = addAccount(
        account = account,
        credential = credential,
        serializer = serializer(),
    )

    internal suspend fun <T : Any> addAccount(
        account: UiAccount,
        credential: T,
        serializer: KSerializer<T>,
    ) {
        withAccountChanges {
            val existingAccount = appDatabase.accountDao().getAccount(account.accountKey)
            require(existingAccount == null || existingAccount.platformId == account.platformId) {
                "This account belongs to another platform"
            }
            restoreAccountsLocked(listOf(account.accountKey)) {
                val credentialJson = credential.encodeJson(serializer)
                val dbAccount =
                    existingAccount?.copy(
                        platformId = account.platformId,
                        credential_json = credentialJson,
                        last_active = Clock.System.now().toEpochMilliseconds(),
                    ) ?: DbAccount(
                        account_key = account.accountKey,
                        platformId = account.platformId,
                        credential_json = credentialJson,
                        last_active = Clock.System.now().toEpochMilliseconds(),
                        sort_id = appDatabase.accountDao().getMaxSortId()?.plus(1) ?: 0L,
                    )
                appDatabase.accountDao().insert(dbAccount)
            }
            if (existingAccount == null) {
                check(accountChangeChannel.trySend(AccountChange.Added(account)).isSuccess)
            } else {
                dataSourceCacheMutex.withLock {
                    val dataSource = dataSourceCache.remove(account.accountKey)?.second
                    if (dataSource is AutoCloseable) dataSource.close()
                    dataSourceRevisions.update { it + (account.accountKey to (it[account.accountKey] ?: 0) + 1) }
                }
            }
        }
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

    internal suspend fun setActiveAccount(accountKey: MicroBlogKey) {
        val account = appDatabase.accountDao().getAccount(accountKey)
        if (account != null) {
            if (!platformRegistry.isRegistered(account.platformId)) {
                throw UnsupportedPlatformException(account.platformId)
            }
            appDatabase.accountDao().setLastActive(
                accountKey,
                Clock.System.now().toEpochMilliseconds(),
            )
        }
    }

    internal suspend fun delete(accountKey: MicroBlogKey): Unit =
        withAccountChanges {
            proxyRepository.removeAccount(accountKey)
            deleteAccount(accountKey)
        }

    internal suspend fun deleteAccounts(
        platformId: String,
        ownsCredential: (String) -> Boolean,
    ): Unit =
        withAccountChanges {
            val accounts =
                appDatabase
                    .accountDao()
                    .allAccounts()
                    .first()
                    .filter { it.platformId == platformId && ownsCredential(it.credential_json) }
            accounts.forEach { proxyRepository.removeAccount(it.account_key) }
            accounts.forEach { deleteAccount(it.account_key) }
        }

    private suspend fun deleteAccount(accountKey: MicroBlogKey) {
        org.koin.mp.KoinPlatformTools
            .defaultContext()
            .getOrNull()
            ?.getOrNull<dev.dimension.flare.data.translation.image.ImageTranslationRepository>()
            ?.removeOwner(accountKey)
        appDataStore.composeConfigData.updateData {
            it.copy(
                lastAccounts = it.lastAccounts.filterNot { key -> key == accountKey },
            )
        }
        if (appDatabase.accountDao().getAccount(accountKey) != null) {
            check(accountChangeChannel.trySend(AccountChange.Removed(accountKey)).isSuccess)
        }
        dataSourceCacheMutex.withLock {
            val datasource = dataSourceCache.remove(accountKey)?.second
            if (datasource is AutoCloseable) {
                datasource.close()
            }
        }
        cacheDatabase.connect {
            val accountType = AccountType.Specific(accountKey)
            cacheDatabase.messageDao().exclusiveRoomKeys(accountType).forEach { roomKey ->
                cacheDatabase.messageDao().deleteRoomMessages(roomKey)
                cacheDatabase.messageDao().deleteRoomReference(roomKey)
                cacheDatabase.messageDao().deleteRoom(roomKey)
            }
            cacheDatabase.listDao().deleteExclusiveMembersByAccountType(accountType)
            cacheDatabase.listDao().deletePagingByAccountType(accountType)
            cacheDatabase.listDao().deleteByAccountType(accountType)
            cacheDatabase.userDao().deleteRelationsByAccountType(accountType)
            cacheDatabase.translationDao().deleteByAccountType(accountType)
            cacheDatabase.statusReferenceDao().deleteByAccountType(accountType)
            cacheDatabase.pagingTimelineDao().deletePresentationReferencesByAccountType(accountType)
            invalidateCachedContent(accountKey)
        }
        appDatabase.connect {
            draftRepository.deleteAccount(accountKey)
            appDatabase.accountDao().delete(accountKey)
        }
        // Finish selection cleanup before a replacement account can be added.
        listOf(appDataStore.notificationPreferences, appDataStore.discoverPreferences).forEach { preferences ->
            try {
                preferences.updateData {
                    if (it.selectedAccountKey == accountKey) it.copy(selectedAccountKey = null) else it
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                error.printStackTrace()
            }
        }
    }

    private suspend fun invalidateCachedContent(accountKey: MicroBlogKey) {
        cacheDatabase.clearHomeTimelinesForAccount(accountKey)
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
    }

    internal fun getFlow(accountKey: MicroBlogKey): Flow<UiState<UiAccount>> =
        appDatabase.accountDao().get(accountKey).combine(platformRegistry.platforms) { account, _ ->
            if (account == null) {
                UiState.Error(NoActiveAccountException)
            } else {
                UiState.Success(account.toUi(platformRegistry))
            }
        }

    internal suspend fun find(accountKey: MicroBlogKey): UiAccount? =
        appDatabase
            .accountDao()
            .get(accountKey)
            .firstOrNull()
            ?.toUi(platformRegistry)

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

    internal suspend fun invalidatePlatform(platformId: String) {
        val accounts =
            appDatabase
                .accountDao()
                .allAccounts()
                .first()
                .filter { it.platformId == platformId }
        accounts.forEach { account ->
            val previous = dataSourceCacheMutex.withLock { dataSourceCache.remove(account.account_key)?.second }
            if (previous is AutoCloseable) previous.close()
            invalidateCachedContent(account.account_key)
        }
    }

    internal suspend fun getOrCreateDataSource(account: UiAccount): MicroblogDataSource {
        val spec = platformRegistry.require(account.platformId)
        val (revision, existing) =
            dataSourceCacheMutex.withLock { dataSourceRevisions.value[account.accountKey] to dataSourceCache[account.accountKey] }
        if (existing?.first === spec) return existing.second
        val dbAccount =
            appDatabase.accountDao().getAccount(account.accountKey)
                ?: throw NoSuchElementException("Account not found: ${account.accountKey}")
        val created = spec.createDataSource(RepositoryPlatformDataSourceContext(account.accountKey, dbAccount.credential_json))
        val cached =
            dataSourceCacheMutex.withLock {
                if (platformRegistry.get(account.platformId) !== spec || dataSourceRevisions.value[account.accountKey] != revision) {
                    return@withLock null
                }
                val current = dataSourceCache[account.accountKey]
                if (current?.first === spec) {
                    current.second
                } else {
                    (current?.second as? AutoCloseable)?.close()
                    dataSourceCache[account.accountKey] = spec to created
                    created
                }
            }
        if (cached !== created && created is AutoCloseable) created.close()
        return cached ?: getOrCreateDataSource(account)
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

        override suspend fun invalidateCachedContent() {
            this@AccountRepository.invalidateCachedContent(accountKey)
        }
    }
}

private fun DbAccount.toUi(platformRegistry: PlatformRegistry): UiAccount {
    val registered = platformRegistry.get(platformId)?.metadata
    val displayName = registered?.displayName ?: platformId
    val icon = registered?.icon ?: UiIcon.Info.asType()
    val displayNameText = registered?.displayNameText ?: UiText.Raw(displayName)
    return UiAccount(
        accountKey = account_key,
        platformId = platformId,
        platformDisplayName = displayName,
        platformIcon = icon,
        platformDisplayNameText = displayNameText,
        platformAvailable = platformRegistry.isRegistered(platformId),
        supportsRelayManagement = platformRegistry.supports(platformId, PlatformCapability.RelayManagement),
    )
}

@HiddenFromObjC
public data object NoActiveAccountException : Exception("No active account.")

@HiddenFromObjC
public class NoAvailableAccountException(
    public val platformIds: List<String>,
) : Exception("No available account. Unregistered platforms: ${platformIds.joinToString()}")

@Immutable
public data class LoginExpiredException(
    val accountKey: MicroBlogKey,
    val platformId: String,
) : Exception("Login expired.")

@Immutable
public data class RequireReLoginException(
    val accountKey: MicroBlogKey,
    val platformId: String,
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
                repository.platformRegistry.defaultGuest.guestDataSource(
                    host = "mastodon.social",
                    locale = Locale.language,
                ),
            )
        }

        is AccountType.GuestHost -> {
            flowOf(
                repository.platformRegistry.defaultGuest.guestDataSource(
                    host = accountType.host,
                    locale = Locale.language,
                ),
            )
        }

        is AccountType.Specific -> {
            repository
                .getFlow(accountType.accountKey)
                .mapNotNull { it.takeSuccess() }
                .combine(repository.platformRegistry.platforms) { account, platforms ->
                    account to platforms.firstOrNull { it.platformId == account.platformId }
                }.distinctUntilChanged()
                .combine(repository.dataSourceRevisions.map { it[accountType.accountKey] }.distinctUntilChanged()) { account, _ -> account }
                .mapLatest { (account, spec) ->
                    if (spec == null) UnavailableMicroblogDataSource else repository.getOrCreateDataSource(account)
                }.distinctUntilChanged()
        }
    }

internal fun activeAccountFlow(repository: AccountRepository): Flow<UiAccount?> =
    repository
        .activeAccount
        .map { it.takeSuccess() }
        .distinctUntilChangedBy { it?.accountKey }

internal fun allAccountServicesFlow(repository: AccountRepository): Flow<ImmutableList<MicroblogDataSource>> =
    repository.allAccountServices.map { accounts -> accounts.map { it.dataSource }.toImmutableList() }.distinctUntilChanged()
