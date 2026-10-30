package dev.dimension.flare.ui.presenter

import androidx.datastore.core.DataStoreFactory
import androidx.room3.Room
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.protobufSerializer
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.DataSourceCapabilityProvider
import dev.dimension.flare.data.datasource.microblog.DataSourceCapabilitySet
import dev.dimension.flare.data.datasource.microblog.DirectMessageDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.NotificationCapability
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.NotificationTimelineDataSource
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.SearchCapability
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.DirectMessageHandler
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.createDataStoreStorage
import dev.dimension.flare.data.datastore.model.AccountSelectionPreferences
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.LocalFilterRepository
import dev.dimension.flare.data.repository.MxgaRepository
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.data.translation.NoopPreTranslationService
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.di.testSingle
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.model.PlatformRuntimeData
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.testPlatformRegistry
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.createSampleUser
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.dm.DirectMessageAvailabilityPresenter
import dev.dimension.flare.ui.presenter.dm.DirectMessageUserListPresenter
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.DiscoverPresenter
import dev.dimension.flare.ui.presenter.home.DiscoverState
import dev.dimension.flare.ui.presenter.home.SearchPresenter
import dev.dimension.flare.ui.presenter.home.SearchState
import dev.dimension.flare.ui.presenter.home.SecondaryTabsPresenter
import dev.dimension.flare.ui.presenter.home.notificationAccountsFlow
import dev.dimension.flare.ui.presenter.home.rememberDiscoverSearchState
import dev.dimension.flare.ui.presenter.settings.AccountsPresenter
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.unavailableAccountService
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverAccountRestorationTest {
    private val firstAccount = MicroBlogKey("alice", "example.social")
    private val savedAccount = MicroBlogKey("bob", "example.social")

    @Test
    fun profileOptionalAccountsRemainVisibleAcrossAccountAndMessagingUi() =
        withFixture {
            profileOptional = true
            insertAccounts()
            val models = MutableStateFlow<AccountSelectors?>(null)
            val page = launchAccountSelectors(models)
            try {
                val state =
                    models.filterNotNull().first {
                        it.active != null &&
                            it.managed?.size == 2 && it.secondary?.size == 2 &&
                            it.dmAvailable && it.directMessages?.size == 2
                    }
                assertTrue(state.active in listOf(firstAccount, savedAccount))
                assertEquals(listOf(firstAccount, savedAccount), state.managed)
                assertEquals(listOf(firstAccount, savedAccount), state.secondary)
                assertEquals(listOf(firstAccount, savedAccount), state.directMessages)
                val notifications = notificationAccountsFlow(repository).first { it.size == 2 }
                assertEquals(setOf(firstAccount, savedAccount), notifications.map { it.profile.key }.toSet())
            } finally {
                page.cancelAndJoin()
            }
        }

    @Test
    fun profileOptionalAccountsAppearInDiscoverAndSearchSelectors() =
        withFixture {
            profileOptional = true
            seedSelection(savedAccount)
            insertAccounts()
            val models = MutableStateFlow<SearchPage?>(null)
            val page = launchSearchPage(models)
            try {
                val state =
                    models.filterNotNull().first {
                        it.discover.isAccountReady && it.discover.selectedAccount != null &&
                            it.discover.accounts
                                .takeSuccess()
                                ?.size == 2 &&
                            it.search.accounts
                                .takeSuccess()
                                ?.size == 2
                    }
                assertEquals(
                    listOf(firstAccount, savedAccount),
                    state.discover.accounts
                        .takeSuccess()!!
                        .map { it.key },
                )
                assertEquals(savedAccount, state.discover.selectedAccountKey)
                assertEquals(savedAccount, state.discover.selectedAccount?.key)
                assertEquals(
                    listOf(firstAccount, savedAccount),
                    state.search.accounts
                        .takeSuccess()!!
                        .map { it.key },
                )
                state.discover.setAccountKey(firstAccount)
                models.filterNotNull().first { it.discover.selectedAccount?.key == firstAccount }
            } finally {
                page.cancelAndJoin()
            }
        }

    @Test
    fun anotherLoadedProfileRemainsSelectableWithoutReplacingTheSavedAccount() =
        withFixture {
            loadedProfileKey = firstAccount
            seedSelection(savedAccount)
            insertAccounts()
            val models = MutableStateFlow<DiscoverState?>(null)
            val page = launchPage(models)
            try {
                val state = models.filterNotNull().first { it.isAccountReady && it.accounts.takeSuccess()?.size == 1 }
                assertEquals(savedAccount, state.selectedAccountKey)
                assertNull(state.selectedAccount)
                assertTrue(state.canSwitchAccount)
                state.setAccountKey(firstAccount)
                models.filterNotNull().first { it.selectedAccountKey == firstAccount }
                assertEquals(
                    firstAccount,
                    appData.discoverPreferences.data
                        .first { it.selectedAccountKey == firstAccount }
                        .selectedAccountKey,
                )
            } finally {
                page.cancelAndJoin()
            }
        }

    @Test
    fun restoredAccountAlsoDrivesSearchBeforeItsProfileLoads() =
        withFixture {
            seedSelection(savedAccount)
            insertAccounts()
            val models = MutableStateFlow<DiscoverState?>(null)
            val page = launchPage(models)
            try {
                repeat(3) { requests.receive() }
                val state = models.filterNotNull().first { it.isAccountReady }
                val search =
                    scope.launch {
                        val presenter = SearchPresenter(state.selectedAccountType, "query")
                        moleculeFlow(RecompositionMode.Immediate) { presenter.body() }.collect {}
                    }
                try {
                    val searches = List(2) { requests.receive() }
                    assertEquals(setOf("searchUsers", "searchStatuses"), searches.map { it.second }.toSet())
                    assertEquals(listOf(AccountType.Specific(savedAccount)), searches.map { it.first }.distinct())
                } finally {
                    search.cancelAndJoin()
                }
            } finally {
                page.cancelAndJoin()
            }
        }

    @Test
    fun storedAccountLoadsAllDiscoverSectionsWithoutWaitingForProfiles() =
        withFixture {
            seedSelection(savedAccount)
            insertAccounts()
            val models = MutableStateFlow<DiscoverState?>(null)
            val page = launchPage(models)
            try {
                val requests = List(3) { requests.receive() }
                assertEquals(setOf("users", "statuses", "hashtags"), requests.map { it.second }.toSet())
                assertEquals(listOf(AccountType.Specific(savedAccount)), requests.map { it.first }.distinct())
                val state = models.filterNotNull().first { it.selectedAccountType == AccountType.Specific(savedAccount) }
                assertNull(state.selectedAccount, "Profile loading must not decide the selected account")
            } finally {
                page.cancelAndJoin()
            }
        }

    @Test
    fun noAccountsStillLoadsGuestDiscovery() =
        withFixture {
            val page = launchPage(MutableStateFlow(null))
            try {
                assertEquals(listOf(AccountType.Guest), List(3) { requests.receive().first }.distinct())
            } finally {
                page.cancelAndJoin()
            }
        }

    @Test
    fun pendingRestorationDoesNotRequestGuestOrTheFirstAccountOrStartSearch() =
        withFixture {
            insertAccounts()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val save =
                scope.launch {
                    appData.discoverPreferences.updateData {
                        entered.complete(Unit)
                        release.await()
                        it.copy(selectedAccountKey = savedAccount)
                    }
                }
            entered.await()
            val models = MutableStateFlow<SearchPage?>(null)
            val page = launchSearchPage(models)
            try {
                val pending = models.filterNotNull().first { it.discover.accounts is dev.dimension.flare.ui.model.UiState.Success }
                assertFalse(pending.discover.isAccountReady)
                assertFalse(pending.search.searching)
                assertTrue(requests.tryReceive().isFailure)
                release.complete(Unit)
                save.join()
                assertEquals(listOf(AccountType.Specific(savedAccount)), List(3) { requests.receive().first }.distinct())
                val restored = models.filterNotNull().first { it.discover.isAccountReady }
                assertFalse(restored.search.searching)
                assertTrue(searchRequests.tryReceive().isFailure)
            } finally {
                release.complete(Unit)
                save.cancelAndJoin()
                page.cancelAndJoin()
            }
        }

    @Test
    fun accountSwitchingReplaysSubmittedSearchesAndClearingForgetsThem() =
        withFixture {
            insertAccounts()
            val models = MutableStateFlow<SearchPage?>(null)
            val page = launchSearchPage(models)
            try {
                val initial = models.filterNotNull().first { it.discover.isAccountReady }
                assertEquals(firstAccount, initial.discover.selectedAccountKey)
                initial.search.search("cats")
                repeat(2) { assertEquals(AccountType.Specific(firstAccount) to "cats", searchRequests.receive()) }

                initial.discover.setAccountKey(savedAccount)
                repeat(2) { assertEquals(AccountType.Specific(savedAccount) to "cats", searchRequests.receive()) }
                val switched = models.filterNotNull().first { it.discover.selectedAccountKey == savedAccount }
                switched.search.search("")
                models.filterNotNull().first { !it.search.searching }
                switched.discover.setAccountKey(firstAccount)

                val cleared = models.filterNotNull().first { it.discover.selectedAccountKey == firstAccount }
                assertFalse(cleared.search.searching)
                assertTrue(searchRequests.tryReceive().isFailure)
            } finally {
                page.cancelAndJoin()
            }
        }

    @Test
    fun searchSubmittedDuringRestorationUsesTheSubmittedQueryAfterInitialization() =
        withFixture {
            insertAccounts()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val save =
                scope.launch {
                    appData.discoverPreferences.updateData {
                        entered.complete(Unit)
                        release.await()
                        it.copy(selectedAccountKey = savedAccount)
                    }
                }
            entered.await()
            val models = MutableStateFlow<SearchPage?>(null)
            val page = launchSearchPage(models)
            try {
                val pending = models.filterNotNull().first { it.discover.accounts is dev.dimension.flare.ui.model.UiState.Success }
                assertFalse(pending.discover.isAccountReady)
                pending.search.search("cats")
                assertTrue(searchRequests.tryReceive().isFailure)
                release.complete(Unit)
                save.join()

                repeat(2) { assertEquals(AccountType.Specific(savedAccount) to "cats", searchRequests.receive()) }
                val restored = models.filterNotNull().first { it.discover.isAccountReady }
                assertTrue(restored.search.searching)
            } finally {
                release.complete(Unit)
                save.cancelAndJoin()
                page.cancelAndJoin()
            }
        }

    @Test
    fun manualSelectionRestoresOnReopenWhileExistingWindowsKeepTheirAccount() =
        withFixture {
            insertAccounts()
            appData.notificationPreferences.updateData { it.copy(selectedAccountKey = firstAccount) }
            val firstModels = MutableStateFlow<DiscoverState?>(null)
            val secondModels = MutableStateFlow<DiscoverState?>(null)
            val first = launchPage(firstModels)
            val second = launchPage(secondModels)
            try {
                firstModels.filterNotNull().first { it.isAccountReady }
                val state = secondModels.filterNotNull().first { it.isAccountReady }
                state.setAccountKey(savedAccount)
                secondModels.filterNotNull().first { it.selectedAccountKey == savedAccount }
                second.cancelAndJoin()

                val reopenedModels = MutableStateFlow<DiscoverState?>(null)
                val reopened = launchPage(reopenedModels)
                try {
                    val restored = reopenedModels.filterNotNull().first { it.isAccountReady }
                    assertEquals(savedAccount, restored.selectedAccountKey)
                    assertEquals(firstAccount, firstModels.value!!.selectedAccountKey)
                    assertEquals(
                        firstAccount,
                        appData.notificationPreferences.data
                            .first()
                            .selectedAccountKey,
                    )
                } finally {
                    reopened.cancelAndJoin()
                }
            } finally {
                first.cancelAndJoin()
                second.cancelAndJoin()
            }
        }

    private fun withFixture(block: suspend Fixture.() -> Unit) =
        runBlocking {
            val main = Executors.newSingleThreadExecutor { Thread(it, "discover-ui") }.asCoroutineDispatcher()
            Dispatchers.setMain(main)
            val fixture = Fixture(CoroutineScope(main + SupervisorJob()))
            try {
                withContext(main) { withTimeout(15_000) { fixture.block() } }
            } finally {
                fixture.close()
                Dispatchers.resetMain()
                main.close()
            }
        }

    private data class AccountSelectors(
        val active: MicroBlogKey?,
        val managed: List<MicroBlogKey>?,
        val secondary: List<MicroBlogKey>?,
        val dmAvailable: Boolean,
        val directMessages: List<MicroBlogKey>?,
    )

    private data class SearchPage(
        val discover: DiscoverState,
        val search: SearchState,
    )

    private inner class Fixture(
        val scope: CoroutineScope,
    ) {
        val root = createTestRootPath()
        val storage = OkioFileStorage(createTestFileSystem(), root)
        val app = Room.memoryDatabaseBuilder<AppDatabase>().setDriver(createDatabaseDriver()).build()
        val cache = Room.memoryDatabaseBuilder<CacheDatabase>().setDriver(createDatabaseDriver()).build()
        val appData = AppDataStore(storage)
        val requests = Channel<Pair<AccountType, String>>(Channel.UNLIMITED)
        val searchRequests = Channel<Pair<AccountType, String>>(Channel.UNLIMITED)
        var loadedProfileKey: MicroBlogKey? = null
        var profileOptional = false
        val registry =
            PlatformRegistry(
                PlatformRuntimeData(
                    platformSpecs =
                        listOf(
                            object : PlatformSpec by testPlatformRegistry().defaultGuest {
                                override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource =
                                    if (profileOptional) IdentitySource(context.accountKey) else AccountSource(context.accountKey)

                                override fun guestDataSource(
                                    host: String,
                                    locale: String,
                                ): MicroblogDataSource = Source(AccountType.Guest)
                            },
                        ),
                    extraTimelineSpecs = emptyList(),
                ),
            )
        val repository =
            AccountRepository(
                app,
                scope,
                appData,
                cache,
                ProxyRepository(storage),
                registry,
                dev.dimension.flare.data.repository.DraftRepository(
                    app,
                    dev.dimension.flare.data.repository
                        .DraftMediaStore(
                            dev.dimension.flare.data.io
                                .InMemoryFileStorage(),
                        ),
                ),
            )

        init {
            val resolver = TimelineResolver(PlatformRuntimeData(emptyList(), emptyList()), unavailableAccountService())
            val settings = SettingsRepository(storage, appData, resolver, cache)
            startKoin {
                modules(
                    module {
                        testSingle { app }
                        testSingle { cache }
                        testSingle { appData }
                        testSingle { repository }
                        testSingle { settings }
                        testSingle { registry }
                        testSingle<CoroutineScope> { scope }
                        testSingle<PreTranslationService> { NoopPreTranslationService }
                        testSingle { LocalFilterRepository(app, scope) }
                        testSingle { MxgaRepository(storage, settings, scope) }
                    },
                )
            }
        }

        suspend fun seedSelection(key: MicroBlogKey) {
            val job = SupervisorJob()
            try {
                DataStoreFactory
                    .create(
                        storage =
                            createDataStoreStorage(
                                "discover_preferences.pb",
                                protobufSerializer(AccountSelectionPreferences()),
                                storage,
                            ),
                        scope = CoroutineScope(Dispatchers.IO + job),
                    ).updateData { it.copy(selectedAccountKey = key) }
            } finally {
                job.cancelAndJoin()
            }
        }

        suspend fun insertAccounts() {
            listOf(firstAccount, savedAccount).forEachIndexed { index, key ->
                app.accountDao().insert(
                    DbAccount(key, "{}", registry.defaultGuest.platformId, last_active = index.toLong(), sort_id = index.toLong()),
                )
            }
        }

        fun launchAccountSelectors(models: MutableStateFlow<AccountSelectors?>) =
            scope.launch {
                val active = ActiveAccountPresenter()
                val managed = AccountsPresenter()
                val secondary = SecondaryTabsPresenter()
                val available = DirectMessageAvailabilityPresenter()
                val messages = DirectMessageUserListPresenter()
                moleculeFlow(RecompositionMode.Immediate) {
                    AccountSelectors(
                        active
                            .body()
                            .user
                            .takeSuccess()
                            ?.key,
                        managed
                            .body()
                            .accounts
                            .takeSuccess()
                            ?.map { it.account.accountKey },
                        secondary
                            .body()
                            .items
                            .takeSuccess()
                            ?.map { it.accountKey },
                        available.body().hasAvailableAccount,
                        messages
                            .body()
                            .items
                            .takeSuccess()
                            ?.map { it.profile.takeSuccess()?.key ?: it.accountKey },
                    )
                }.collect { models.value = it }
            }

        fun launchPage(models: MutableStateFlow<DiscoverState?>) =
            scope.launch {
                val presenter = DiscoverPresenter()
                moleculeFlow(RecompositionMode.Immediate) { presenter.body() }.collect { models.value = it }
            }

        fun launchSearchPage(models: MutableStateFlow<SearchPage?>) =
            scope.launch {
                val presenter = DiscoverPresenter()
                moleculeFlow(RecompositionMode.Immediate) {
                    val state = presenter.body()
                    SearchPage(state, rememberDiscoverSearchState(state.selectedAccountType, state.isAccountReady))
                }.collect { models.value = it }
            }

        suspend fun close() {
            scope.coroutineContext.job.cancelAndJoin()
            app.close()
            cache.close()
            stopKoin()
            deleteTestRootPath(root)
        }

        private open inner class Source(
            private val accountType: AccountType,
        ) : MicroblogDataSource {
            override fun homeTimeline() = notSupported<UiTimelineV2>()

            override fun userTimeline(
                userKey: MicroBlogKey,
                mediaOnly: Boolean,
            ) = notSupported<UiTimelineV2>()

            override fun context(statusKey: MicroBlogKey) = notSupported<UiTimelineV2>()

            override fun searchStatus(query: String) =
                notSupported<UiTimelineV2>().also {
                    requests.trySend(accountType to "searchStatuses")
                    searchRequests.trySend(accountType to query)
                }

            override fun searchUser(query: String) =
                notSupported<UiProfile>().also {
                    requests.trySend(accountType to "searchUsers")
                    searchRequests.trySend(accountType to query)
                }

            override fun following(userKey: MicroBlogKey) = notSupported<UiProfile>()

            override fun fans(userKey: MicroBlogKey) = notSupported<UiProfile>()

            override suspend fun profileTabs(userKey: MicroBlogKey) = persistentListOf<ProfileTab>()

            override fun discoverUsers() = notSupported<UiProfile>().also { requests.trySend(accountType to "users") }

            override fun discoverStatuses() = notSupported<UiTimelineV2>().also { requests.trySend(accountType to "statuses") }

            override fun discoverHashtags() = notSupported<UiHashtag>().also { requests.trySend(accountType to "hashtags") }
        }

        private inner class IdentitySource(
            override val authenticatedAccountKey: MicroBlogKey,
        ) : Source(AccountType.Specific(authenticatedAccountKey)),
            DataSourceCapabilityProvider {
            override val authenticatedProfile =
                createSampleUser().copy(
                    key = authenticatedAccountKey,
                    nameInternal = authenticatedAccountKey.id.toUiPlainText(),
                )
            override val capabilitySet =
                DataSourceCapabilitySet(
                    search = SearchCapability(this),
                    notification =
                        NotificationCapability(
                            timeline =
                                object : NotificationTimelineDataSource, MicroblogDataSource by this {
                                    override val accountKey = authenticatedAccountKey
                                    override val supportedNotificationFilter = listOf(NotificationFilter.All)

                                    override fun notification(type: NotificationFilter) = notSupported<UiTimelineV2>()
                                },
                            events = null,
                        ),
                    directMessage =
                        object : DirectMessageDataSource, MicroblogDataSource by this {
                            override val accountKey = authenticatedAccountKey
                            override val directMessageHandler: DirectMessageHandler get() = error("Unused")
                            override val directMessageBadgeCount = Cacheable(fetchSource = {}, cacheSource = { flowOf(0) })
                        },
                )
        }

        private inner class AccountSource(
            override val accountKey: MicroBlogKey,
        ) : Source(AccountType.Specific(accountKey)),
            AuthenticatedMicroblogDataSource,
            UserDataSource {
            override val userHandler =
                UserHandler(
                    accountKey.host,
                    object : UserLoader {
                        override suspend fun userById(id: String): UiProfile {
                            check(accountKey == loadedProfileKey) { "profile unavailable" }
                            return UiProfile(
                                key = accountKey,
                                handle = UiHandle(raw = accountKey.id, host = accountKey.host),
                                avatar = "",
                                nameInternal = accountKey.id.toUiPlainText(),
                                platformId = registry.defaultGuest.platformId,
                                clickEvent = ClickEvent.Noop,
                                banner = null,
                                description = null,
                                matrices = UiProfile.Matrices(0, 0, 0, null),
                                mark = persistentListOf(),
                                bottomContent = null,
                            )
                        }

                        override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile = error("profile unavailable")
                    },
                )
        }
    }
}
