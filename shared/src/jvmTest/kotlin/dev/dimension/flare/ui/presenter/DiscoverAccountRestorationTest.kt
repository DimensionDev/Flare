package dev.dimension.flare.ui.presenter

import androidx.datastore.core.DataStoreFactory
import androidx.room3.Room
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.common.protobufSerializer
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
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
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.home.DiscoverPresenter
import dev.dimension.flare.ui.presenter.home.DiscoverState
import dev.dimension.flare.ui.presenter.home.SearchPresenter
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverAccountRestorationTest {
    private val firstAccount = MicroBlogKey("alice", "example.social")
    private val savedAccount = MicroBlogKey("bob", "example.social")

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
    fun pendingRestorationDoesNotRequestGuestOrTheFirstAccount() =
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
            val models = MutableStateFlow<DiscoverState?>(null)
            val page = launchPage(models)
            try {
                models.filterNotNull().first { it.accounts is dev.dimension.flare.ui.model.UiState.Success }
                assertTrue(!models.value!!.isAccountReady)
                assertTrue(requests.tryReceive().isFailure)
                release.complete(Unit)
                save.join()
                assertEquals(listOf(AccountType.Specific(savedAccount)), List(3) { requests.receive().first }.distinct())
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

    private inner class Fixture(
        val scope: CoroutineScope,
    ) {
        val root = createTestRootPath()
        val storage = OkioFileStorage(createTestFileSystem(), root)
        val app = Room.memoryDatabaseBuilder<AppDatabase>().setDriver(createDatabaseDriver()).build()
        val cache = Room.memoryDatabaseBuilder<CacheDatabase>().setDriver(createDatabaseDriver()).build()
        val appData = AppDataStore(storage)
        val requests = Channel<Pair<AccountType, String>>(Channel.UNLIMITED)
        var loadedProfileKey: MicroBlogKey? = null
        val registry =
            PlatformRegistry(
                PlatformRuntimeData(
                    platformSpecs =
                        listOf(
                            object : PlatformSpec by testPlatformRegistry().defaultGuest {
                                override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource =
                                    AccountSource(context.accountKey)

                                override fun guestDataSource(
                                    host: String,
                                    locale: String,
                                ): MicroblogDataSource = Source(AccountType.Guest)
                            },
                        ),
                    extraTimelineSpecs = emptyList(),
                ),
            )
        val repository = AccountRepository(app, scope, appData, cache, registry)

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

        fun launchPage(models: MutableStateFlow<DiscoverState?>) =
            scope.launch {
                val presenter = DiscoverPresenter()
                moleculeFlow(RecompositionMode.Immediate) { presenter.body() }.collect { models.value = it }
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
                notSupported<UiTimelineV2>().also { requests.trySend(accountType to "searchStatuses") }

            override fun searchUser(query: String) = notSupported<UiProfile>().also { requests.trySend(accountType to "searchUsers") }

            override fun following(userKey: MicroBlogKey) = notSupported<UiProfile>()

            override fun fans(userKey: MicroBlogKey) = notSupported<UiProfile>()

            override suspend fun profileTabs(userKey: MicroBlogKey) = persistentListOf<ProfileTab>()

            override fun discoverUsers() = notSupported<UiProfile>().also { requests.trySend(accountType to "users") }

            override fun discoverStatuses() = notSupported<UiTimelineV2>().also { requests.trySend(accountType to "statuses") }

            override fun discoverHashtags() = notSupported<UiHashtag>().also { requests.trySend(accountType to "hashtags") }
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
