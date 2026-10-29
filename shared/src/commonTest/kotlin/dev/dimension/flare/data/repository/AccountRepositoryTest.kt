package dev.dimension.flare.data.repository

import androidx.datastore.core.DataStore
import androidx.room3.Room
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.createTestFileItem
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbDirectMessageTimeline
import dev.dimension.flare.data.database.cache.model.DbList
import dev.dimension.flare.data.database.cache.model.DbListMember
import dev.dimension.flare.data.database.cache.model.DbListPaging
import dev.dimension.flare.data.database.cache.model.DbMessageItem
import dev.dimension.flare.data.database.cache.model.DbMessageRoom
import dev.dimension.flare.data.database.cache.model.DbMessageRoomReference
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusReference
import dev.dimension.flare.data.database.cache.model.DbTimelineItemPresentationReference
import dev.dimension.flare.data.database.cache.model.DbTimelineItemPresentationType
import dev.dimension.flare.data.database.cache.model.DbTranslation
import dev.dimension.flare.data.database.cache.model.DbUserRelation
import dev.dimension.flare.data.database.cache.model.TranslationEntityType
import dev.dimension.flare.data.database.cache.model.TranslationStatus
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.UnavailableMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.TimelineTabConfigurationDataSource
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AccountSelectionPreferences
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.data.model.tab.ShortcutSpec
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.platform.CommonTimelineSpecs
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.model.UnsupportedPlatformException
import dev.dimension.flare.testPlatformRegistry
import dev.dimension.flare.testPlatformRuntimeData
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.model.createSampleStatus
import dev.dimension.flare.ui.model.createSampleUser
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.compose.SaveDraftUseCase
import dev.dimension.flare.ui.presenter.home.RememberedAccountSelection
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AccountRepositoryTest : RobolectricTest() {
    @Test
    fun platformDeletionRemovesOnlyOwnedAccountsAndTheirDraftTargets() =
        runTest {
            val repository = createRepository(this)
            val drafts = DraftRepository(appDatabase, DraftMediaStore(fileStorage))
            val owned = UiAccount(MicroBlogKey("owned", "same.example"), "TestPlugin")
            val second = UiAccount(MicroBlogKey("second", "same.example"), "TestPlugin")
            val foreign = UiAccount(MicroBlogKey("foreign", "same.example"), "TestPlugin")
            val builtIn = UiAccount(MicroBlogKey("builtin", "same.example"), "Mastodon")
            listOf(owned, second, builtIn).forEach { repository.addAccount(it, "owned") }
            repository.addAccount(foreign, "foreign")
            val content =
                dev.dimension.flare.data.database.app.model.DraftContent(
                    "keep shared content",
                    dev.dimension.flare.ui.model.UiTimelineV2.Post.Visibility.Public,
                    emptyList(),
                    false,
                )

            fun media(group: String): SaveDraftMedia {
                val path = fileStorage.draftMediaFile(group, "test.png")
                fileStorage.createDirectories(requireNotNull(path.parent))
                fileStorage.write(path, byteArrayOf(1, 2, 3))
                return SaveDraftMedia(path.toString(), mediaType = dev.dimension.flare.data.database.app.model.DraftMediaType.IMAGE)
            }
            val sharedMedia = media("shared")
            val ownedMedia = media("owned")
            drafts.saveDraft(
                SaveDraftInput(
                    "shared",
                    content,
                    listOf(SaveDraftTarget(owned.accountKey), SaveDraftTarget(builtIn.accountKey)),
                    listOf(sharedMedia),
                ),
            )
            drafts.saveDraft(
                SaveDraftInput(
                    "owned",
                    content,
                    listOf(SaveDraftTarget(owned.accountKey), SaveDraftTarget(second.accountKey)),
                    listOf(ownedMedia),
                ),
            )
            repeat(2) { repository.deleteAccounts("TestPlugin") { it == "\"owned\"" } }
            assertEquals(
                setOf(foreign.accountKey, builtIn.accountKey),
                repository.allAccounts
                    .first()
                    .map { it.accountKey }
                    .toSet(),
            )
            assertNull(drafts.draft("owned").first())
            assertEquals(
                listOf(builtIn.accountKey),
                drafts
                    .draft("shared")
                    .first()
                    ?.targets
                    ?.map { it.accountKey },
            )
            assertTrue(fileStorage.exists(sharedMedia.cachePath.toPath()))
            assertFalse(fileStorage.exists(ownedMedia.cachePath.toPath()))
        }

    @Test
    fun deletingAccountsRemovesOwnedCachesAndPreservesSharedData() =
        runTest {
            val repository = createRepository(this)
            val owned = AccountType.Specific(accountKey)
            val survivor = AccountType.Specific(otherAccountKey)
            repository.addAccount(UiAccount(accountKey, "Mastodon"), "owned")
            repository.addAccount(UiAccount(otherAccountKey, "Mastodon"), "survivor")
            val user = createSampleUser()
            val sample = createSampleStatus(user)
            val ownedRoom = MicroBlogKey("private-room", "example.social")
            val sharedRoom = MicroBlogKey("shared-room", "example.social")
            listOf(ownedRoom, sharedRoom).forEach { key ->
                val message =
                    UiDMItem(key, user, UiDMItem.Message.Text("private message".toUiPlainText()), sample.createdAt, false, null, false)
                cacheDatabase.messageDao().insertMessages(listOf(DbMessageItem(key, key, user.key, 0, message, false)))
                cacheDatabase.messageDao().insert(listOf(DbMessageRoom(key, "Mastodon", key)))
                cacheDatabase.messageDao().insertReferences(listOf(DbMessageRoomReference(key, user.key)))
                cacheDatabase.messageDao().insertTimeline(
                    listOf(DbDirectMessageTimeline(owned, key, 0, 0, UiDMRoom(key, persistentListOf(user), message, 0))),
                )
                cacheDatabase.listDao().insertAllList(listOf(DbList(key, owned, DbList.ListContent(UiList.List(key.id, key.id)))))
                cacheDatabase.listDao().insertAllPaging(listOf(DbListPaging(owned, "owned-lists", key)))
                cacheDatabase.listDao().insertAllMember(listOf(DbListMember(key, user.key)))
            }
            cacheDatabase.messageDao().insertTimeline(
                listOf(DbDirectMessageTimeline(survivor, sharedRoom, 0, 0, UiDMRoom(sharedRoom, persistentListOf(user), null, 0))),
            )
            cacheDatabase.listDao().insertAllList(
                listOf(DbList(sharedRoom, survivor, DbList.ListContent(UiList.List(sharedRoom.id, sharedRoom.id)))),
            )
            cacheDatabase.listDao().insertAllPaging(listOf(DbListPaging(survivor, "surviving-lists", sharedRoom)))
            val statuses =
                listOf(owned, survivor).map { type ->
                    val status = DbStatus(sample.statusKey, type, sample.copy(accountType = type), 0, "private post")
                    cacheDatabase.statusDao().insertAll(listOf(status))
                    cacheDatabase.userDao().insertUserRelation(DbUserRelation(type, user.key, UiRelation(following = true)))
                    cacheDatabase.translationDao().insert(
                        DbTranslation(TranslationEntityType.Status, status.id, "en", "hash", TranslationStatus.Completed, updatedAt = 0),
                    )
                    cacheDatabase.statusReferenceDao().insertAll(
                        listOf(DbStatusReference(ReferenceType.Quote, status.id, status.id)),
                    )
                    val timeline = DbPagingTimeline("cache-test", status.id, 0)
                    cacheDatabase.pagingTimelineDao().insertAll(listOf(timeline))
                    cacheDatabase.pagingTimelineDao().insertPresentationReferences(
                        listOf(
                            DbTimelineItemPresentationReference(
                                timeline.pagingKey,
                                timeline._id,
                                status.id,
                                DbTimelineItemPresentationType.Quote,
                            ),
                        ),
                    )
                    status
                }
            repository.delete(accountKey)
            assertNull(cacheDatabase.messageDao().getMessage(ownedRoom))
            assertNotNull(cacheDatabase.messageDao().getMessage(sharedRoom))
            assertTrue(
                cacheDatabase
                    .messageDao()
                    .getRoomTimeline(owned)
                    .first()
                    .isEmpty(),
            )
            assertTrue(
                cacheDatabase
                    .listDao()
                    .getListMemberKeysFlow(ownedRoom)
                    .first()
                    .isEmpty(),
            )
            assertEquals(listOf(user.key), cacheDatabase.listDao().getListMemberKeysFlow(sharedRoom).first())
            assertTrue(
                cacheDatabase
                    .listDao()
                    .getListKeysFlow("owned-lists")
                    .first()
                    .isEmpty(),
            )
            assertNull(cacheDatabase.listDao().getList(ownedRoom, owned).first())
            assertNotNull(cacheDatabase.listDao().getList(sharedRoom, survivor).first())
            assertNull(cacheDatabase.userDao().getUserRelation(owned, user.key).first())
            assertNotNull(cacheDatabase.userDao().getUserRelation(survivor, user.key).first())
            assertNull(cacheDatabase.translationDao().get(TranslationEntityType.Status, statuses[0].id, "en"))
            assertNotNull(cacheDatabase.translationDao().get(TranslationEntityType.Status, statuses[1].id, "en"))
            assertTrue(cacheDatabase.statusReferenceDao().getByStatusId(statuses[0].id).isEmpty())
            assertEquals(1, cacheDatabase.statusReferenceDao().getByStatusId(statuses[1].id).size)
            assertEquals(listOf(statuses[1].id), cacheDatabase.statusDao().getVersions(statuses.map { it.id }).map { it.id })
            assertEquals(
                listOf(statuses[1].id),
                cacheDatabase
                    .pagingTimelineDao()
                    .getPresentationReferences(
                        "cache-test",
                        statuses.map {
                            DbPagingTimeline.createId("cache-test", it.id)
                        },
                    ).map { it.referenceStatusId },
            )
            repository.delete(otherAccountKey)
            assertNull(cacheDatabase.messageDao().getMessage(sharedRoom))
            assertTrue(
                cacheDatabase
                    .listDao()
                    .getListMemberKeysFlow(sharedRoom)
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun staleDraftSavePreservesSurvivingSharedDraftAndMedia() =
        runTest {
            val repository = createRepository(this)
            val mediaStore = DraftMediaStore(fileStorage)
            val drafts = DraftRepository(appDatabase, mediaStore)
            val saveDraft = SaveDraftUseCase(drafts, mediaStore)
            val accounts = listOf(UiAccount(accountKey, "Mastodon"), UiAccount(otherAccountKey, "Mastodon"))
            accounts.forEach { repository.addAccount(it, "credential") }
            val bytes = byteArrayOf(1, 2, 3)
            val originalMedia =
                ComposeData.Media(
                    file = createTestFileItem(root, "old.png", bytes, dev.dimension.flare.common.FileType.Image),
                    altText = null,
                )
            val original =
                ComposeDraftBundle(
                    accounts,
                    ComposeData(content = "keep shared draft", medias = listOf(originalMedia)),
                    "shared-late-save",
                )
            saveDraft(original)
            val before = assertNotNull(drafts.draft(original.groupId).first())
            repository.delete(accountKey)
            val changedMedia =
                ComposeData.Media(
                    file = createTestFileItem(root, "new.png", byteArrayOf(9), dev.dimension.flare.common.FileType.Image),
                    altText = null,
                )
            val stale = original.copy(template = original.template.copy(medias = listOf(changedMedia)))
            assertFailsWith<IllegalStateException> { saveDraft(stale) }
            val after = assertNotNull(drafts.draft(original.groupId).first())
            assertEquals(listOf(otherAccountKey), after.targets.map { it.accountKey })
            assertEquals(before.content, after.content)
            assertEquals(before.medias, after.medias)
            assertContentEquals(
                bytes,
                fileStorage.read(
                    after.medias
                        .single()
                        .cachePath
                        .toPath(),
                ),
            )
            assertFailsWith<IllegalStateException> { saveDraft(stale.copy(groupId = "new-late-save")) }
            assertNull(drafts.draft("new-late-save").first())
            assertFalse(fileStorage.exists(requireNotNull(fileStorage.draftMediaFile("new-late-save", "file").parent)))
        }

    @Test
    fun staleUnavailableAndFailedAccountEventsDoNotStopLaterTabSynchronization() =
        runTest {
            val registry = testPlatformRegistry()
            val laterAccountCreated = CompletableDeferred<Unit>()
            val failedAccountAttempted = CompletableDeferred<Unit>()

            fun platform(id: String) =
                object : PlatformSpec by registry.defaultGuest {
                    override val platformId = id
                    override val isDefaultGuest = false

                    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource {
                        if (id == "BrokenPlugin") {
                            failedAccountAttempted.complete(Unit)
                            throw IllegalArgumentException("Invalid plugin credential")
                        }
                        if (context.accountKey == otherAccountKey) laterAccountCreated.complete(Unit)
                        return object : MicroblogDataSource by UnavailableMicroblogDataSource, TimelineTabConfigurationDataSource {
                            override val defaultTabs =
                                persistentListOf(CommonTimelineSpecs.home.candidate(TimelineSpec.AccountBasedData(context.accountKey)))
                            override val builtInTimelineTabs = defaultTabs
                            override val shortcuts = persistentListOf<ShortcutSpec>()
                        }
                    }
                }

            listOf("PluginTest", "UnavailablePlugin", "BrokenPlugin").forEach { registry.replaceExternal(it, platform(it)) }
            val repository = createRepository(backgroundScope, registry = registry)
            repository.addAccount(UiAccount(accountKey, "PluginTest"), "deleted")
            repository.delete(accountKey)
            repository.addAccount(UiAccount(MicroBlogKey("unavailable", "example.social"), "UnavailablePlugin"), "unavailable")
            registry.replaceExternal("UnavailablePlugin", null)
            repository.addAccount(UiAccount(MicroBlogKey("broken", "example.social"), "BrokenPlugin"), "invalid")
            repository.addAccount(UiAccount(otherAccountKey, "PluginTest"), "valid")

            val resolver = TimelineResolver(testPlatformRuntimeData(), RepositoryAccountService(repository))
            val settings = SettingsRepository(fileStorage, appDataStore, resolver, cacheDatabase)
            val job = SupervisorJob()
            val scope =
                CoroutineScope(
                    backgroundScope.coroutineContext + job +
                        CoroutineExceptionHandler { _, error -> laterAccountCreated.completeExceptionally(error) },
                )
            val tabId = "common.home:$otherAccountKey"
            try {
                AccountTabSyncCoordinator(repository, settings, scope, resolver)
                withContext(Dispatchers.Default) {
                    withTimeout(5_000) {
                        laterAccountCreated.await()
                        settings.tabSettingsV2.first { it.homeSlots.any { slot -> slot.id == tabId } }
                    }
                }
                assertTrue(failedAccountAttempted.isCompleted)
                repository.delete(otherAccountKey)
                withContext(Dispatchers.Default) {
                    withTimeout(5_000) {
                        settings.tabSettingsV2.first { it.homeSlots.none { slot -> slot.id == tabId } }
                    }
                }
            } finally {
                job.cancelAndJoin()
            }
        }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun accountObserversRebindAcrossPluginReloadRemovalAndReattachment() =
        runTest {
            val registry = testPlatformRegistry()
            val first =
                object : MicroblogDataSource by
                UnavailableMicroblogDataSource {}
            val second =
                object : MicroblogDataSource by
                UnavailableMicroblogDataSource {}

            fun platform(
                source: MicroblogDataSource,
                id: String = "PluginTest",
            ) = object : PlatformSpec by registry.defaultGuest {
                override val platformId = id
                override val isDefaultGuest = false

                override fun createDataSource(context: PlatformDataSourceContext) = source
            }
            registry.replaceExternal("PluginTest", platform(first))
            val repository = createRepository(backgroundScope, registry = registry)
            repository.addAccount(UiAccount(accountKey, "PluginTest"), "stored credential")
            val emitted = Channel<MicroblogDataSource>(Channel.UNLIMITED)
            backgroundScope.launch {
                accountServiceFlow(
                    AccountType
                        .Specific(accountKey),
                    repository,
                ).collect(emitted::send)
            }
            assertSame(first, emitted.receive())
            registry.replaceExternal("Unrelated", platform(first, "Unrelated"))
            runCurrent()
            assertTrue(emitted.tryReceive().isFailure)
            repository.invalidatePlatform("PluginTest")
            registry.replaceExternal("PluginTest", platform(second))
            assertSame(second, emitted.receive())
            assertEquals("stored credential", repository.credentialFlow<String>(accountKey).first())
            registry.replaceExternal("PluginTest", null)
            assertSame(UnavailableMicroblogDataSource, emitted.receive())
            assertFalse(
                repository.allAccounts
                    .first()
                    .single()
                    .platformAvailable,
            )
            registry.replaceExternal("PluginTest", platform(first))
            assertSame(first, emitted.receive())
            assertTrue(
                repository.allAccounts
                    .first()
                    .single()
                    .platformAvailable,
            )
        }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun reloginRebindsExistingSingleAndAllAccountCollectorsWithoutRebindingTokenRefreshes() =
        runTest {
            val registry = testPlatformRegistry()
            val first = object : MicroblogDataSource by UnavailableMicroblogDataSource {}
            val second = object : MicroblogDataSource by UnavailableMicroblogDataSource {}
            var current: MicroblogDataSource = first
            registry.replaceExternal(
                "PluginTest",
                object : PlatformSpec by registry.defaultGuest {
                    override val platformId = "PluginTest"
                    override val isDefaultGuest = false

                    override fun createDataSource(context: PlatformDataSourceContext) = current
                },
            )
            val repository = createRepository(backgroundScope, registry = registry)
            val account = UiAccount(accountKey, "PluginTest")
            repository.addAccount(account, "first negotiated capabilities")
            val single = Channel<MicroblogDataSource>(Channel.UNLIMITED)
            val all = Channel<List<MicroblogDataSource>>(Channel.UNLIMITED)
            val service = Channel<List<AccountMicroblogDataSource>>(Channel.UNLIMITED)
            backgroundScope.launch {
                accountServiceFlow(AccountType.Specific(accountKey), repository).collect(single::send)
            }
            backgroundScope.launch { allAccountServicesFlow(repository).collect(all::send) }
            backgroundScope.launch { RepositoryAccountService(repository).allAccountServicesFlow().collect(service::send) }
            assertSame(first, single.receive())
            assertSame(first, all.receive().single())
            assertSame(first, service.receive().single().dataSource)

            current = second
            repository.addAccount(account, "changed capabilities and stricter compose limits")
            assertSame(second, withTimeoutOrNull(5_000) { single.receive() })
            assertSame(second, withTimeoutOrNull(5_000) { all.receive() }?.single())
            assertSame(second, withTimeoutOrNull(5_000) { service.receive() }?.single()?.dataSource)

            repository.updateCredential(accountKey, "refreshed token").join()
            assertEquals("refreshed token", repository.credentialFlow<String>(accountKey).first())
            runCurrent()
            assertSame(second, repository.getOrCreateDataSource(account))
            assertTrue(single.tryReceive().isFailure)
            assertTrue(all.tryReceive().isFailure)
            assertTrue(service.tryReceive().isFailure)
        }

    private val root = createTestRootPath()
    private val fileStorage = OkioFileStorage(createTestFileSystem(), root)

    private lateinit var appDatabase: AppDatabase
    private lateinit var cacheDatabase: CacheDatabase
    private lateinit var appDataStore: AppDataStore

    private val accountKey = MicroBlogKey(id = "alice", host = "example.social")
    private val otherAccountKey = MicroBlogKey(id = "bob", host = "example.social")

    @BeforeTest
    fun setup() {
        appDatabase =
            Room
                .memoryDatabaseBuilder<AppDatabase>()
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        cacheDatabase =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        appDataStore = AppDataStore(fileStorage)
    }

    @AfterTest
    fun tearDown() {
        appDatabase.close()
        cacheDatabase.close()
        deleteTestRootPath(root)
    }

    @Test
    fun startupCompletesInterruptedDeletionAndAllowsRelogin() =
        runTest {
            insertAccount()
            val proxy = ProxyRepository(fileStorage)
            proxy.selectAccount(accountKey, ProxyPolicy(ProxyMode.DIRECT))
            appDataStore.composeConfigData.updateData { it.copy(lastAccounts = listOf(accountKey)) }
            proxy.removeAccount(accountKey)
            val repository = createRepository(this, proxy)
            val other = MicroBlogKey("other", "example.social")
            repository.addAccount(UiAccount(other, "Mastodon"), "unaffected credentials")
            assertNull(appDatabase.accountDao().getAccount(accountKey))
            assertEquals(
                emptyList(),
                appDataStore.composeConfigData.data
                    .first()
                    .lastAccounts,
            )
            assertEquals(setOf(accountKey.toString()), proxy.settings.first().removedAccounts)

            repository.addAccount(UiAccount(accountKey, "Mastodon"), "new credentials")
            assertEquals(accountKey, repository.find(accountKey)?.accountKey)
            assertFalse(accountKey.toString() in proxy.settings.first().removedAccounts)
        }

    @Test
    fun reloginWaitsForPendingDeletionRecovery() =
        runTest {
            insertAccount()
            val proxy = ProxyRepository(fileStorage)
            proxy.removeAccount(accountKey)
            val repository = createRepository(this, proxy)
            repository.addAccount(UiAccount(accountKey, "Mastodon"), "new credentials")
            assertEquals(accountKey, repository.find(accountKey)?.accountKey)
            assertFalse(accountKey.toString() in proxy.settings.first().removedAccounts)
        }

    @Test
    fun accountAdditionIsPublishedBeforeTheNextAccountMutationStarts() =
        runTest {
            val store = ControlledProxyStore(ProxySettings(removedAccounts = setOf(accountKey.toString())))
            val repository = createRepository(this, ProxyRepository(store))
            repository.withAccountChanges { }
            val writeEntered = CompletableDeferred<Unit>()
            val releaseWrite = CompletableDeferred<Unit>()
            store.beforeWrite = {
                writeEntered.complete(Unit)
                releaseWrite.await()
            }
            val events = Channel<String>(Channel.UNLIMITED)
            val observer =
                launch(Dispatchers.Unconfined) {
                    repository.accountChanges
                        .filterIsInstance<AccountChange.Added>()
                        .map { it.account }
                        .collect { events.send("added") }
                }
            val addition = launch { repository.addAccount(UiAccount(accountKey, "Mastodon"), "restored") }
            val nextMutation =
                launch(Dispatchers.Unconfined, start = CoroutineStart.LAZY) {
                    repository.withAccountChanges { events.send("mutation-start") }
                }
            try {
                writeEntered.await()
                // Run the queued mutation during unlock, exposing an announcement sent after the lock was released.
                nextMutation.start()
                assertNull(events.tryReceive().getOrNull(), "An account must not be announced before restoration commits")
                assertFalse(nextMutation.isCompleted, "The next mutation must wait for restoration")

                releaseWrite.complete(Unit)
                addition.join()
                nextMutation.join()

                assertEquals(listOf("added", "mutation-start"), List(2) { events.tryReceive().getOrNull() })
                assertEquals(accountKey, repository.find(accountKey)?.accountKey)
                assertEquals(emptySet(), store.settings.value.removedAccounts)
            } finally {
                releaseWrite.complete(Unit)
                addition.cancelAndJoin()
                nextMutation.cancelAndJoin()
                observer.cancelAndJoin()
            }
        }

    @Test
    fun failedAccountRestorationDoesNotPublishAdded() =
        runTest {
            val removed = setOf(accountKey.toString())
            val store = ControlledProxyStore(ProxySettings(removedAccounts = removed))
            val repository = createRepository(this, ProxyRepository(store))
            repository.withAccountChanges { }
            val failure = IllegalStateException("Proxy restoration failed")
            store.beforeWrite = { throw failure }
            val events = Channel<UiAccount>(Channel.UNLIMITED)
            val observer =
                launch(Dispatchers.Unconfined) {
                    repository.accountChanges
                        .filterIsInstance<AccountChange.Added>()
                        .map { it.account }
                        .collect { events.send(it) }
                }
            try {
                val thrown =
                    assertFailsWith<IllegalStateException> {
                        repository.addAccount(UiAccount(accountKey, "Mastodon"), "restored")
                    }
                assertEquals(failure.message, thrown.message)
                assertNull(appDatabase.accountDao().getAccount(accountKey))
                assertEquals(removed, store.settings.value.removedAccounts)
                assertNull(events.tryReceive().getOrNull(), "A rolled-back account must not be announced")
            } finally {
                observer.cancelAndJoin()
            }
        }

    @Test
    fun allAccountsDoesNotReEmitWhenActiveTimestampChanges() =
        runTest {
            val repository = createRepository(this)
            insertAccount()
            val emissions = Channel<List<UiAccount>>(Channel.UNLIMITED)
            val job =
                launch {
                    repository.allAccounts.collect {
                        emissions.send(it.toList())
                    }
                }

            try {
                val initial = emissions.receive()
                assertEquals(listOf(accountKey), initial.map { it.accountKey })

                repository.setActiveAccount(accountKey)

                val duplicate = withTimeoutOrNull(100) { emissions.receive() }
                assertNull(
                    duplicate,
                    "allAccounts emitted again even though account keys and sort order did not change",
                )
            } finally {
                job.cancel()
            }
        }

    @Test
    fun accountProfileFetchIsNotRepeatedWhenAccountTableUpdateDoesNotChangeAccountList() =
        runTest {
            val repository = createRepository(this)
            insertAccount()
            val profileCache = MutableStateFlow<String?>(null)
            val fetches = Channel<Unit>(Channel.UNLIMITED)
            var fetchCount = 0

            fun profileFlow(account: UiAccount) =
                Cacheable(
                    fetchSource = {
                        fetchCount += 1
                        fetches.send(Unit)
                        profileCache.value = account.accountKey.id
                    },
                    cacheSource = {
                        profileCache.filterNotNull()
                    },
                ).toUi()

            val accountProfiles =
                repository
                    .allAccounts
                    .map { accounts ->
                        accounts.map(::profileFlow)
                    }.combineLatestFlowLists()

            val job =
                launch {
                    accountProfiles.collect()
                }

            try {
                fetches.receive()
                assertEquals(1, fetchCount)

                repository.setActiveAccount(accountKey)

                val duplicateFetch = withTimeoutOrNull(100) { fetches.receive() }
                assertNull(
                    duplicateFetch,
                    "Profile fetch was triggered again after an account table update that did not change the account list",
                )
                assertEquals(1, fetchCount)
            } finally {
                job.cancel()
            }
        }

    @Test
    fun accountProfileFetchIsNotRestartedWhileCacheIsEmptyWhenAccountListReEmits() =
        runTest {
            val repository = createRepository(this)
            insertAccount()
            val emptyProfileCache = MutableStateFlow<String?>(null)
            val fetches = Channel<Unit>(Channel.UNLIMITED)
            var fetchCount = 0

            fun loadingProfileFlow(account: UiAccount) =
                Cacheable(
                    fetchSource = {
                        fetchCount += 1
                        fetches.send(Unit)
                        awaitCancellation()
                    },
                    cacheSource = {
                        emptyProfileCache.filterNotNull()
                    },
                ).toUi()

            val accountProfiles =
                repository
                    .allAccounts
                    .map { accounts ->
                        accounts.map(::loadingProfileFlow)
                    }.combineLatestFlowLists()

            val job =
                launch {
                    accountProfiles.collect()
                }

            try {
                fetches.receive()
                assertEquals(1, fetchCount)

                repository.setActiveAccount(accountKey)

                val restartedFetch = withTimeoutOrNull(100) { fetches.receive() }
                assertNull(
                    restartedFetch,
                    "Profile fetch was restarted while the profile cache was empty and the account list did not change",
                )
                assertEquals(1, fetchCount)
            } finally {
                job.cancel()
            }
        }

    @Test
    fun unavailableAccountCanBeDisplayedExportedAndDeletedWithoutDatasource() =
        runTest {
            val repository = createRepository(this)
            val unavailableKey = MicroBlogKey(id = "alice", host = "testnet.example")
            appDatabase.accountDao().insert(
                DbAccount(
                    account_key = unavailableKey,
                    credential_json = "{\"token\":\"kept\"}",
                    platformId = "TestNet",
                    last_active = 10L,
                    sort_id = 3L,
                ),
            )

            val account = repository.allAccounts.first().single()
            assertEquals("TestNet", account.platformDisplayName)
            assertEquals(UiIcon.Info.asType(), account.platformIcon)
            assertFalse(account.platformAvailable)
            assertFailsWith<UnsupportedPlatformException> {
                repository.getOrCreateDataSource(account)
            }
            val activationError =
                assertFailsWith<UnsupportedPlatformException> {
                    repository.setActiveAccount(unavailableKey)
                }
            assertEquals("TestNet", activationError.platformId)
            assertEquals("{\"token\":\"kept\"}", appDatabase.accountDao().getAccount(unavailableKey)?.credential_json)

            repository.delete(unavailableKey)
            assertNull(appDatabase.accountDao().getAccount(unavailableKey))
        }

    @Test
    fun unavailableAccountUsesItsPlatformIdWithoutPersistedDisplayMetadata() =
        runTest {
            val repository = createRepository(this)
            val account =
                UiAccount(
                    accountKey = MicroBlogKey("alice", "future.example"),
                    platformId = "FutureNet",
                    platformDisplayName = "Future Network",
                    platformIcon = IconType.Url("file:///stable/future.png"),
                    platformDisplayNameText = UiText.Raw("Localized Future Network"),
                )

            repository.addAccount(account, "credential")

            val restored = repository.allAccounts.first().single()
            assertEquals("FutureNet", restored.platformDisplayName)
            assertEquals(UiIcon.Info.asType(), restored.platformIcon)
            assertEquals(UiText.Raw("FutureNet"), restored.platformDisplayNameText)
            assertFalse(restored.platformAvailable)
        }

    @Test
    fun activeAccountSkipsNewerUnavailableAccounts() =
        runTest {
            val repository = createRepository(this)
            insertAccount()
            appDatabase.accountDao().insert(
                DbAccount(
                    account_key = MicroBlogKey("future", "testnet.example"),
                    credential_json = "{}",
                    platformId = "TestNet",
                    last_active = 100L,
                    sort_id = 1L,
                ),
            )

            val state = repository.activeAccount.first()
            assertEquals(accountKey, assertIs<dev.dimension.flare.ui.model.UiState.Success<UiAccount>>(state).data.accountKey)
        }

    @Test
    fun allUnavailableAccountsHaveExplicitState() =
        runTest {
            val repository = createRepository(this)
            appDatabase.accountDao().insert(
                DbAccount(
                    account_key = MicroBlogKey("future", "testnet.example"),
                    credential_json = "{}",
                    platformId = "TestNet",
                    last_active = 100L,
                ),
            )

            val state = repository.activeAccount.first()
            val error = assertIs<dev.dimension.flare.ui.model.UiState.Error<UiAccount>>(state).throwable
            assertEquals(listOf("TestNet"), assertIs<NoAvailableAccountException>(error).platformIds)
        }

    @Test
    fun deletingAccountClearsItsSavedNotificationSelectionEvenWhenThePageIsClosed() =
        runTest {
            val repository = createRepository(this)
            insertAccount()
            appDataStore.notificationPreferences.updateData { it.copy(selectedAccountKey = accountKey) }

            repository.delete(accountKey)

            assertNull(
                appDataStore.notificationPreferences.data
                    .first()
                    .selectedAccountKey,
            )
            assertNull(appDatabase.accountDao().getAccount(accountKey))
        }

    @Test
    fun deletingAnotherAccountPreservesTheSavedNotificationSelection() =
        runTest {
            val repository = createRepository(this)
            insertAccount()
            val savedAccount = MicroBlogKey("bob", "another.social")
            appDataStore.notificationPreferences.updateData { it.copy(selectedAccountKey = savedAccount) }

            repository.delete(accountKey)

            assertEquals(
                savedAccount,
                appDataStore.notificationPreferences.data
                    .first()
                    .selectedAccountKey,
            )
        }

    @Test
    fun deletingAccountClearsItsSavedDiscoverSelectionEvenWhenThePageIsClosed() =
        runTest {
            val repository = createRepository(this)
            insertAccount()
            appDataStore.discoverPreferences.updateData { it.copy(selectedAccountKey = accountKey) }

            repository.delete(accountKey)

            assertNull(
                appDataStore.discoverPreferences.data
                    .first()
                    .selectedAccountKey,
            )
            assertNull(appDatabase.accountDao().getAccount(accountKey))
        }

    @Test
    fun deletingAnotherAccountPreservesTheSavedDiscoverSelection() =
        runTest {
            val repository = createRepository(this)
            insertAccount()
            val savedAccount = MicroBlogKey("bob", "another.social")
            appDataStore.discoverPreferences.updateData { it.copy(selectedAccountKey = savedAccount) }

            repository.delete(accountKey)

            assertEquals(
                savedAccount,
                appDataStore.discoverPreferences.data
                    .first()
                    .selectedAccountKey,
            )
        }

    @Test
    fun notificationSelectionSavedDuringDeletionDoesNotReturnAfterReaddingAccount() =
        runTest {
            checkSelectionSavedDuringDeletion(appDataStore.notificationPreferences, accountKey, expectedSaved = null)
        }

    @Test
    fun discoverSelectionSavedDuringDeletionDoesNotReturnAfterReaddingAccount() =
        runTest {
            checkSelectionSavedDuringDeletion(appDataStore.discoverPreferences, accountKey, expectedSaved = null)
        }

    @Test
    fun notificationChoiceOfAnotherAccountDuringDeletionIsPreserved() =
        runTest {
            checkSelectionSavedDuringDeletion(appDataStore.notificationPreferences, otherAccountKey, expectedSaved = otherAccountKey)
        }

    @Test
    fun discoverChoiceOfAnotherAccountDuringDeletionIsPreserved() =
        runTest {
            checkSelectionSavedDuringDeletion(appDataStore.discoverPreferences, otherAccountKey, expectedSaved = otherAccountKey)
        }

    private suspend fun CoroutineScope.checkSelectionSavedDuringDeletion(
        preferences: DataStore<AccountSelectionPreferences>,
        selectedDuringDeletion: MicroBlogKey,
        expectedSaved: MicroBlogKey?,
    ) {
        val repository = createRepository(this)
        insertAccount()
        appDatabase.accountDao().insert(DbAccount(otherAccountKey, "{}", "Mastodon", last_active = 2L, sort_id = 1L))
        val keys = repository.allAccounts.first().map { it.accountKey }
        val openPage =
            RememberedAccountSelection(preferences, this, repository.accountMutationMutex, accountExists = {
                repository.find(it) !=
                    null
            })
        openPage.updateAccounts(keys)
        openPage.restore()
        openPage.selectAccount(otherAccountKey)
        val closedPage =
            RememberedAccountSelection(preferences, this, repository.accountMutationMutex, accountExists = {
                repository.find(it) !=
                    null
            })
        closedPage.updateAccounts(keys)
        closedPage.restore()
        closedPage.selectAccount(accountKey)
        preferences.updateData { it }

        val concurrentSelection =
            launch(Dispatchers.Unconfined) {
                assertEquals(
                    accountKey,
                    repository.accountChanges
                        .filterIsInstance<AccountChange.Removed>()
                        .map { it.accountKey }
                        .first(),
                )
                openPage.selectAccount(selectedDuringDeletion)
                // The page closes after this click; only the app-scoped save remains.
            }
        try {
            repository.delete(accountKey)
            concurrentSelection.join()

            assertNull(repository.find(accountKey))
            repository.addAccount(UiAccount(accountKey, "Mastodon"), "readded")
            assertEquals(expectedSaved, preferences.updateData { it }.selectedAccountKey)
            val reopened =
                RememberedAccountSelection(preferences, this, repository.accountMutationMutex, accountExists = {
                    repository.find(it) !=
                        null
                })
            reopened.updateAccounts(repository.allAccounts.first().map { it.accountKey })
            reopened.restore()
            assertEquals(otherAccountKey, reopened.selectedAccountKey.first())
        } finally {
            concurrentSelection.cancel()
        }
    }

    @Test
    fun lateNotificationSelectionCannotRestoreADeletedAccount() =
        runTest {
            checkLatePageSelection(appDataStore.notificationPreferences)
        }

    @Test
    fun lateDiscoverSelectionCannotRestoreADeletedAccount() =
        runTest {
            checkLatePageSelection(appDataStore.discoverPreferences)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun TestScope.checkLatePageSelection(preferences: DataStore<AccountSelectionPreferences>) {
        val repository = createRepository(this)
        insertAccount()
        appDatabase.accountDao().insert(DbAccount(otherAccountKey, "{}", "Mastodon", last_active = 2L, sort_id = 1L))
        preferences.updateData { it.copy(selectedAccountKey = otherAccountKey) }
        val openPage =
            RememberedAccountSelection(preferences, this, repository.accountMutationMutex, accountExists = {
                repository.find(it) !=
                    null
            })
        openPage.restore()
        val removalObserved = CompletableDeferred<Unit>()
        val deliverRemoval = CompletableDeferred<Unit>()
        val saveEntered = CompletableDeferred<Unit>()
        val releaseSave = CompletableDeferred<Unit>()
        val pendingSave =
            launch(start = CoroutineStart.LAZY) {
                preferences.updateData {
                    saveEntered.complete(Unit)
                    releaseSave.await()
                    it
                }
            }
        val page =
            launch {
                repository.allAccounts.collect { accounts ->
                    val keys = accounts.map { it.accountKey }
                    if (accountKey !in keys) {
                        removalObserved.complete(Unit)
                        deliverRemoval.await()
                    }
                    openPage.updateAccounts(keys)
                }
            }
        try {
            openPage.selectedAccountKey.first { it == otherAccountKey }
            repository.delete(accountKey)
            removalObserved.await()
            assertNull(repository.find(accountKey))
            assertEquals(otherAccountKey, preferences.updateData { it }.selectedAccountKey)

            pendingSave.start()
            saveEntered.await()
            openPage.selectAccount(accountKey)
            page.cancelAndJoin()

            val readded = launch { repository.addAccount(UiAccount(accountKey, "Mastodon"), "readded") }
            runCurrent()
            releaseSave.complete(Unit)
            pendingSave.join()
            readded.join()
            val reopened =
                RememberedAccountSelection(preferences, this, repository.accountMutationMutex, accountExists = {
                    repository.find(it) !=
                        null
                })
            reopened.updateAccounts(repository.allAccounts.first().map { it.accountKey })
            reopened.restore()
            assertEquals(otherAccountKey, reopened.selectedAccountKey.first())
        } finally {
            releaseSave.complete(Unit)
            pendingSave.cancelAndJoin()
            page.cancelAndJoin()
            deliverRemoval.complete(Unit)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun deletionPreservesAFreshSelectionAfterReaddingTheSameAccount() =
        runTest {
            val repository = createRepository(this)
            insertAccount()
            appDatabase.accountDao().insert(DbAccount(otherAccountKey, "{}", "Mastodon", last_active = 2L, sort_id = 1L))
            appDataStore.notificationPreferences.updateData { it.copy(selectedAccountKey = otherAccountKey) }
            appDataStore.discoverPreferences.updateData { it.copy(selectedAccountKey = otherAccountKey) }
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val pendingNotificationSave =
                launch {
                    appDataStore.notificationPreferences.updateData {
                        entered.complete(Unit)
                        release.await()
                        it
                    }
                }
            entered.await()
            try {
                val deletion = launch { repository.delete(accountKey) }
                repository.allAccounts.first { accounts -> accounts.none { it.accountKey == accountKey } }
                val readded = launch { repository.addAccount(UiAccount(accountKey, "Mastodon"), "new credential") }
                runCurrent()
                // Re-login may wait for removal to finish instead of overlapping its cleanup.
                if (!readded.isCompleted) {
                    release.complete(Unit)
                    deletion.join()
                }
                readded.join()
                val currentKeys = repository.allAccounts.first().map { it.accountKey }
                assertEquals(listOf(otherAccountKey, accountKey), currentKeys)
                val newPage =
                    RememberedAccountSelection(appDataStore.discoverPreferences, this, repository.accountMutationMutex, accountExists = {
                        repository.find(it) !=
                            null
                    })
                newPage.updateAccounts(currentKeys)
                newPage.restore()
                newPage.selectAccount(accountKey)
                assertEquals(accountKey, appDataStore.discoverPreferences.updateData { it }.selectedAccountKey)
                release.complete(Unit)
                deletion.join()
                val reopened =
                    RememberedAccountSelection(appDataStore.discoverPreferences, this, repository.accountMutationMutex, accountExists = {
                        repository.find(it) !=
                            null
                    })
                reopened.updateAccounts(repository.allAccounts.first().map { it.accountKey })
                reopened.restore()
                assertEquals(accountKey, reopened.selectedAccountKey.first())
            } finally {
                release.complete(Unit)
                pendingNotificationSave.join()
            }
        }

    @Test
    fun delayedAccountRemovalDoesNotClearANewSelectionAfterReaddingAccount() =
        runTest {
            val repository = createRepository(this)
            insertAccount()
            appDatabase.accountDao().insert(DbAccount(otherAccountKey, "{}", "Mastodon", last_active = 2L, sort_id = 1L))
            val preferences = appDataStore.discoverPreferences
            val oldPage =
                RememberedAccountSelection(preferences, this, repository.accountMutationMutex, accountExists = {
                    repository.find(it) !=
                        null
                })
            oldPage.updateAccounts(repository.allAccounts.first().map { it.accountKey })
            oldPage.restore()
            oldPage.selectAccount(accountKey)
            preferences.updateData { it }
            repository.delete(accountKey)
            val delayedRemoval = repository.allAccounts.first().map { it.accountKey }
            repository.addAccount(UiAccount(accountKey, "Mastodon"), "new credential")
            val newPage =
                RememberedAccountSelection(preferences, this, repository.accountMutationMutex, accountExists = {
                    repository.find(it) !=
                        null
                })
            newPage.updateAccounts(repository.allAccounts.first().map { it.accountKey })
            newPage.restore()
            newPage.selectAccount(accountKey)
            preferences.updateData { it }

            oldPage.updateAccounts(delayedRemoval)
            val reopened =
                RememberedAccountSelection(preferences, this, repository.accountMutationMutex, accountExists = {
                    repository.find(it) != null
                })
            reopened.updateAccounts(repository.allAccounts.first().map { it.accountKey })
            reopened.restore()
            assertEquals(accountKey, reopened.selectedAccountKey.first())
        }

    @Test
    fun addingAnotherPlatformCannotReplaceAnExistingAccount() =
        runTest {
            val repository = createRepository(this)
            repository.addAccount(UiAccount(accountKey, "Mastodon"), "original")
            val original = appDatabase.accountDao().getAccount(accountKey)

            assertFailsWith<IllegalArgumentException> {
                repository.addAccount(UiAccount(accountKey, "OtherPlugin"), "replacement")
            }

            assertEquals(original, appDatabase.accountDao().getAccount(accountKey))
        }

    @Test
    fun reloginDoesNotPublishANewAccountEvent() =
        runTest {
            val repository = createRepository(this)
            val events = Channel<AccountChange>(Channel.UNLIMITED)
            val collection = launch { repository.accountChanges.collect(events::send) }
            val alice = UiAccount(accountKey, "Mastodon")
            val bob = UiAccount(MicroBlogKey("bob", "example.social"), "Mastodon")

            try {
                repository.addAccount(alice, "first")
                assertEquals(alice.accountKey, assertIs<AccountChange.Added>(events.receive()).account.accountKey)
                repository.addAccount(bob, "second")
                assertEquals(bob.accountKey, assertIs<AccountChange.Added>(events.receive()).account.accountKey)

                repository.addAccount(alice.copy(platformDisplayName = "Updated metadata"), "refreshed")

                assertNull(withTimeoutOrNull(100) { events.receive() })
                val stored = appDatabase.accountDao().getAccount(accountKey)
                assertEquals("Mastodon", stored?.platformId)
                assertEquals("\"refreshed\"", stored?.credential_json)
            } finally {
                collection.cancel()
            }
        }

    @Test
    fun deletingAndReaddingTheSameAccountPublishesBothEventsAgain() =
        runTest {
            val repository = createRepository(this)
            val events = Channel<AccountChange>(Channel.UNLIMITED)
            val collection = launch { repository.accountChanges.collect(events::send) }
            val account = UiAccount(accountKey, "Mastodon")

            try {
                repository.addAccount(account, "first")
                assertEquals(accountKey, assertIs<AccountChange.Added>(events.receive()).account.accountKey)

                repository.delete(accountKey)
                assertEquals(accountKey, assertIs<AccountChange.Removed>(events.receive()).accountKey)

                repository.addAccount(account, "second")
                assertEquals(accountKey, assertIs<AccountChange.Added>(events.receive()).account.accountKey)
            } finally {
                collection.cancel()
            }
        }

    @Test
    fun accountChangesAreBufferedAndKeepMutationOrder() =
        runTest {
            val repository = createRepository(this)
            val alice = UiAccount(accountKey, "Mastodon")
            val bob = UiAccount(MicroBlogKey("bob", "example.social"), "Mastodon")

            repository.addAccount(alice, "first")
            repository.addAccount(bob, "second")
            repository.delete(accountKey)
            repository.addAccount(alice, "third")

            val changes = repository.accountChanges.take(4).toList()
            assertEquals(
                listOf(
                    "added:${alice.accountKey}",
                    "added:${bob.accountKey}",
                    "removed:${alice.accountKey}",
                    "added:${alice.accountKey}",
                ),
                changes.map { change ->
                    when (change) {
                        is AccountChange.Added -> "added:${change.account.accountKey}"
                        is AccountChange.Removed -> "removed:${change.accountKey}"
                    }
                },
            )
        }

    private class ControlledProxyStore(
        initial: ProxySettings,
    ) : DataStore<ProxySettings> {
        val settings = MutableStateFlow(initial)
        override val data = settings
        var beforeWrite: suspend () -> Unit = {}

        override suspend fun updateData(transform: suspend (ProxySettings) -> ProxySettings): ProxySettings {
            val updated = transform(settings.value)
            beforeWrite()
            settings.value = updated
            return updated
        }
    }

    private fun createRepository(
        scope: CoroutineScope,
        proxy: ProxyRepository = ProxyRepository(fileStorage),
        registry: PlatformRegistry = testPlatformRegistry(),
    ): AccountRepository =
        AccountRepository(
            appDatabase = appDatabase,
            coroutineScope = scope,
            appDataStore = appDataStore,
            cacheDatabase = cacheDatabase,
            proxyRepository = proxy,
            platformRegistry = registry,
            draftRepository = DraftRepository(appDatabase, DraftMediaStore(fileStorage)),
        )

    private suspend fun insertAccount() {
        appDatabase.accountDao().insert(
            DbAccount(
                account_key = accountKey,
                credential_json = "{}",
                platformId = "Mastodon",
                last_active = 1L,
                sort_id = 0L,
            ),
        )
    }
}
