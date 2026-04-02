package dev.dimension.flare.data.datasource.microblog

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.Locale
import dev.dimension.flare.common.OnDeviceAI
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.TranslationDisplayOptions
import dev.dimension.flare.data.database.cache.model.TranslationEntityType
import dev.dimension.flare.data.database.cache.model.TranslationStatus
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.data.datasource.microblog.paging.TimelineRemoteMediator
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.network.ai.AiCompletionService
import dev.dimension.flare.data.network.ai.OpenAIService
import dev.dimension.flare.data.translation.OnlinePreTranslationService
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.data.translation.aiPreTranslateConfig
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.TranslationDocument
import dev.dimension.flare.ui.render.TranslationTokenKind
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import okio.Path
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MixedRemoteMediatorTest : RobolectricTest() {
    private val root = createTestRootPath()
    private val pathProducer =
        object : PlatformPathProducer {
            override fun dataStoreFile(fileName: String): Path = root.resolve(fileName)

            override fun draftMediaFile(
                groupId: String,
                fileName: String,
            ): Path = root.resolve("draft_media").resolve(groupId).resolve(fileName)
        }

    private lateinit var db: CacheDatabase

    @BeforeTest
    fun setup() {
        startKoin {
            modules(
                module {
                    single<PlatformFormatter> { TestFormatter() }
                },
            )
        }
        db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
    }

    @AfterTest
    fun tearDown() {
        db.close()
        stopKoin()
        deleteTestRootPath(root)
    }

    @Test
    fun prependReturnsEndWithoutLoadingSubMediators() =
        runTest {
            val first = FakeLoader("a") { PagingResult(nextKey = "next_a") }
            val second = FakeLoader("b") { PagingResult(nextKey = "next_b") }
            val mediator = MixedRemoteMediator(db, listOf(first, second))

            val result = mediator.load(pageSize = 20, request = PagingRequest.Prepend("ignored"))

            assertNull(result.nextKey)
            assertEquals(0, first.requests.size)
            assertEquals(0, second.requests.size)
        }

    @Test
    fun appendUsesRemainingMediatorsAndRefreshResetsMediatorSet() =
        runTest {
            val first =
                FakeLoader("a") { request ->
                    when (request) {
                        PagingRequest.Refresh ->
                            PagingResult(
                                data = listOf(feed("https://example.com/a_refresh", 1000L)),
                                nextKey = "a_next",
                            )

                        is PagingRequest.Append -> {
                            assertEquals("a_next", request.nextKey)
                            PagingResult(
                                data = listOf(feed("https://example.com/a_append", 900L)),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Prepend -> error("Prepend should not be requested here")
                    }
                }

            val second =
                FakeLoader("b") { request ->
                    when (request) {
                        PagingRequest.Refresh ->
                            PagingResult(
                                data = listOf(feed("https://example.com/b_refresh", 2000L)),
                                nextKey = null,
                            )

                        is PagingRequest.Append -> error("Second mediator should be filtered out before append")
                        is PagingRequest.Prepend -> error("Prepend should not be requested here")
                    }
                }

            val mediator = MixedRemoteMediator(db, listOf(first, second))

            mediator.load(pageSize = 20, request = PagingRequest.Refresh)
            val appendResult = mediator.load(pageSize = 20, request = PagingRequest.Append("mixed_next_key"))

            assertNull(appendResult.nextKey)
            assertEquals(listOf<PagingRequest>(PagingRequest.Refresh, PagingRequest.Append("a_next")), first.requests)
            assertEquals(listOf<PagingRequest>(PagingRequest.Refresh), second.requests)

            mediator.load(pageSize = 20, request = PagingRequest.Refresh)

            assertEquals(3, first.requests.size)
            assertEquals(PagingRequest.Refresh, first.requests.last())
            assertEquals(2, second.requests.size)
            assertEquals(PagingRequest.Refresh, second.requests.last())
        }

    @Test
    fun failingSubMediatorIsIgnoredAndLoadStillSucceeds() =
        runTest {
            val failing =
                FakeLoader("failing") { _ ->
                    error("network failure")
                }
            val healthy =
                FakeLoader("healthy") { request ->
                    when (request) {
                        PagingRequest.Refresh ->
                            PagingResult(
                                data = listOf(feed("https://example.com/healthy", 3000L)),
                                nextKey = null,
                            )

                        is PagingRequest.Append -> error("No append expected")
                        is PagingRequest.Prepend -> error("No prepend expected")
                    }
                }

            val mediator = MixedRemoteMediator(db, listOf(failing, healthy))
            val result = mediator.load(pageSize = 20, request = PagingRequest.Refresh)

            assertNull(result.nextKey)
            assertEquals(1, failing.requests.size)
            assertEquals(1, healthy.requests.size)
            assertEquals(1, result.data.size)
            assertEquals("https://example.com/healthy", (result.data.first() as UiTimelineV2.Feed).url)
            assertNotNull(db.pagingTimelineDao().getPagingKey("mixed_failing"))
            assertNotNull(db.pagingTimelineDao().getPagingKey("mixed_healthy"))
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun refreshWithMultipleItemsPerSubPersistsSortedOrderInDatabase() =
        runTest {
            val first =
                FakeLoader("a") { request ->
                    when (request) {
                        PagingRequest.Refresh ->
                            PagingResult(
                                data =
                                    listOf(
                                        feed("https://example.com/a_3000", 3000L),
                                        feed("https://example.com/a_1000", 1000L),
                                    ),
                                nextKey = null,
                            )

                        is PagingRequest.Append -> error("No append expected")
                        is PagingRequest.Prepend -> error("No prepend expected")
                    }
                }
            val second =
                FakeLoader("b") { request ->
                    when (request) {
                        PagingRequest.Refresh ->
                            PagingResult(
                                data =
                                    listOf(
                                        feed("https://example.com/b_4000", 4000L),
                                        feed("https://example.com/b_2000", 2000L),
                                    ),
                                nextKey = null,
                            )

                        is PagingRequest.Append -> error("No append expected")
                        is PagingRequest.Prepend -> error("No prepend expected")
                    }
                }

            val mixed = MixedRemoteMediator(db, listOf(first, second))
            val timelineRemoteMediator = TimelineRemoteMediator(loader = mixed, database = db, allowLongText = false)

            val mediatorResult =
                timelineRemoteMediator.load(
                    loadType = LoadType.REFRESH,
                    state =
                        PagingState(
                            pages = emptyList(),
                            anchorPosition = null,
                            config = PagingConfig(pageSize = 20),
                            leadingPlaceholderCount = 0,
                        ),
                )
            assertTrue(mediatorResult is androidx.paging.RemoteMediator.MediatorResult.Success)

            val pagingSource = db.pagingTimelineDao().getPagingSource(timelineRemoteMediator.pagingKey)
            val page =
                pagingSource.load(
                    PagingSource.LoadParams.Refresh(
                        key = null,
                        loadSize = 20,
                        placeholdersEnabled = false,
                    ),
                )
            assertTrue(page is PagingSource.LoadResult.Page)
            val urls =
                page.data.mapNotNull {
                    (it.status.data.content as? UiTimelineV2.Feed)?.url
                }
            assertEquals(
                listOf(
                    "https://example.com/b_4000",
                    "https://example.com/a_3000",
                    "https://example.com/b_2000",
                    "https://example.com/a_1000",
                ),
                urls,
            )
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun refreshCollapsesLinearReplyChainIntoLatestPost() =
        runTest {
            val accountKey = MicroBlogKey("mastodon.example", "timeline")
            val accountType = AccountType.Specific(accountKey)
            val user = profile(MicroBlogKey("mastodon.example", "user"), "User")
            val postA =
                createPost(
                    accountType = accountType,
                    user = user,
                    statusKey = MicroBlogKey("mastodon.example", "a"),
                    text = "A",
                )
            val postB =
                createPost(
                    accountType = accountType,
                    user = user,
                    statusKey = MicroBlogKey("mastodon.example", "b"),
                    text = "B",
                    parents = listOf(postA),
                )
            val postC =
                createPost(
                    accountType = accountType,
                    user = user,
                    statusKey = MicroBlogKey("mastodon.example", "c"),
                    text = "C",
                    parents = listOf(postB),
                )
            val loader =
                FakeLoader("reply_chain") { request ->
                    when (request) {
                        PagingRequest.Refresh ->
                            PagingResult(
                                data = listOf(postC, postB, postA),
                                nextKey = null,
                            )

                        is PagingRequest.Append -> error("No append expected")
                        is PagingRequest.Prepend -> error("No prepend expected")
                    }
                }

            val mediator = TimelineRemoteMediator(loader = loader, database = db, allowLongText = false)
            val mediatorResult =
                mediator.load(
                    loadType = LoadType.REFRESH,
                    state =
                        PagingState(
                            pages = emptyList(),
                            anchorPosition = null,
                            config = PagingConfig(pageSize = 20),
                            leadingPlaceholderCount = 0,
                        ),
                )
            assertTrue(mediatorResult is androidx.paging.RemoteMediator.MediatorResult.Success)

            val pagingSource = db.pagingTimelineDao().getPagingSource(mediator.pagingKey)
            val page =
                pagingSource.load(
                    PagingSource.LoadParams.Refresh(
                        key = null,
                        loadSize = 20,
                        placeholdersEnabled = false,
                    ),
                )
            assertTrue(page is PagingSource.LoadResult.Page)
            assertEquals(1, page.data.size)

            val post =
                assertIs<UiTimelineV2.Post>(
                    TimelinePagingMapper.toUi(
                        item = page.data.single(),
                        pagingKey = mediator.pagingKey,
                        useDbKeyInItemKey = false,
                        translationDisplayOptions =
                            TranslationDisplayOptions(
                                translationEnabled = false,
                                autoDisplayEnabled = false,
                                providerCacheKey = "",
                            ),
                    ),
                )
            assertEquals(postC.statusKey, post.statusKey)
            assertEquals(listOf(postA.statusKey, postB.statusKey), post.parents.map { it.statusKey })
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun refreshSchedulesPreTranslationForRootAndReplyReference() =
        runTest {
            val appDataStore = AppDataStore(pathProducer)
            appDataStore.appSettingsStore.updateData {
                it.copy(
                    language = Locale.language,
                    translateConfig = aiPreTranslateConfig(),
                    aiConfig =
                        AppSettings.AiConfig(
                            type = AppSettings.AiConfig.Type.OnDevice,
                        ),
                )
            }
            val preTranslationService: PreTranslationService =
                OnlinePreTranslationService(
                    database = db,
                    appDataStore = appDataStore,
                    aiCompletionService = AiCompletionService(OpenAIService(), TestOnDeviceAI()),
                    coroutineScope = CoroutineScope(Dispatchers.Unconfined),
                )
            val accountKey = MicroBlogKey(id = "account-pretranslation", host = "test.social")
            val accountType = AccountType.Specific(accountKey)
            val rootUser = profile(MicroBlogKey("root-pretranslation", "test.social"), "Root")
            val parentUser = profile(MicroBlogKey("parent-pretranslation", "test.social"), "Parent")
            val parent =
                createPost(
                    user = parentUser,
                    accountType = accountType,
                    statusKey = MicroBlogKey(id = "parent-status-pretranslation", host = "test.social"),
                    text = "parent source",
                )
            val rootPost =
                createPost(
                    user = rootUser,
                    accountType = accountType,
                    statusKey = MicroBlogKey(id = "root-status-pretranslation", host = "test.social"),
                    text = "root source",
                    parents = listOf(parent),
                )
            val loader =
                FakeLoader("pretranslation") { request ->
                    when (request) {
                        PagingRequest.Refresh ->
                            PagingResult(
                                data = listOf(rootPost),
                                nextKey = null,
                            )

                        is PagingRequest.Append -> error("No append expected")
                        is PagingRequest.Prepend -> error("No prepend expected")
                    }
                }
            val mediator =
                TimelineRemoteMediator(
                    loader = loader,
                    database = db,
                    allowLongText = false,
                    preTranslationService = preTranslationService,
                )

            val mediatorResult =
                mediator.load(
                    loadType = LoadType.REFRESH,
                    state =
                        PagingState(
                            pages = emptyList(),
                            anchorPosition = null,
                            config = PagingConfig(pageSize = 20),
                            leadingPlaceholderCount = 0,
                        ),
                )
            assertTrue(mediatorResult is androidx.paging.RemoteMediator.MediatorResult.Success)
            val savedRoot = db.statusDao().get(rootPost.statusKey, accountType).first()
            val savedParent = db.statusDao().get(parent.statusKey, accountType).first()
            assertNotNull(savedRoot)
            assertNotNull(savedParent)

            val rootTranslation =
                db
                    .translationDao()
                    .find(
                        entityType = TranslationEntityType.Status,
                        entityKey = savedRoot.id,
                        targetLanguage = Locale.language,
                    ).filterNotNull()
                    .first { it.status == TranslationStatus.Completed }
            val parentTranslation =
                db
                    .translationDao()
                    .find(
                        entityType = TranslationEntityType.Status,
                        entityKey = savedParent.id,
                        targetLanguage = Locale.language,
                    ).filterNotNull()
                    .first { it.status == TranslationStatus.Completed }

            assertEquals("root source (${Locale.language})", rootTranslation.payload?.content?.raw)
            assertEquals("parent source (${Locale.language})", parentTranslation.payload?.content?.raw)
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun homeTimelineSkipsPreTranslationForLongTextPosts() =
        runTest {
            val appDataStore = AppDataStore(pathProducer)
            appDataStore.appSettingsStore.updateData {
                it.copy(
                    language = Locale.language,
                    translateConfig = aiPreTranslateConfig(),
                    aiConfig =
                        AppSettings.AiConfig(
                            type = AppSettings.AiConfig.Type.OnDevice,
                        ),
                )
            }
            val preTranslationService: PreTranslationService =
                OnlinePreTranslationService(
                    database = db,
                    appDataStore = appDataStore,
                    aiCompletionService = AiCompletionService(OpenAIService(), TestOnDeviceAI()),
                    coroutineScope = CoroutineScope(Dispatchers.Unconfined),
                )
            val accountKey = MicroBlogKey(id = "account-longtext-home", host = "test.social")
            val longText = buildString { repeat(520) { append('长') } }
            val post =
                createPost(
                    accountType = AccountType.Specific(accountKey),
                    user = profile(MicroBlogKey("user-longtext-home", "test.social"), "User"),
                    statusKey = MicroBlogKey("status-longtext-home", "test.social"),
                    text = longText,
                )
            val loader =
                FakeLoader("home") { request ->
                    when (request) {
                        PagingRequest.Refresh ->
                            PagingResult(
                                data = listOf(post),
                                nextKey = null,
                            )

                        is PagingRequest.Append -> error("No append expected")
                        is PagingRequest.Prepend -> error("No prepend expected")
                    }
                }
            val mediator =
                TimelineRemoteMediator(
                    loader = loader,
                    database = db,
                    allowLongText = false,
                    preTranslationService = preTranslationService,
                )

            val mediatorResult =
                mediator.load(
                    loadType = LoadType.REFRESH,
                    state =
                        PagingState(
                            pages = emptyList(),
                            anchorPosition = null,
                            config = PagingConfig(pageSize = 20),
                            leadingPlaceholderCount = 0,
                        ),
                )
            assertTrue(mediatorResult is androidx.paging.RemoteMediator.MediatorResult.Success)

            val savedStatus = db.statusDao().get(post.statusKey, AccountType.Specific(accountKey)).first()
            assertNotNull(savedStatus)
            val translation =
                db.translationDao().get(
                    entityType = TranslationEntityType.Status,
                    entityKey = savedStatus.id,
                    targetLanguage = Locale.language,
                )
            assertNull(translation)
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun homeTimelineSkipsAiTranslationWhenSourceLanguageMatchesTargetLanguage() =
        runTest {
            val appDataStore = AppDataStore(pathProducer)
            appDataStore.appSettingsStore.updateData {
                it.copy(
                    language = Locale.language,
                    translateConfig = aiPreTranslateConfig(),
                    aiConfig =
                        AppSettings.AiConfig(
                            type = AppSettings.AiConfig.Type.OnDevice,
                        ),
                )
            }
            val preTranslationService: PreTranslationService =
                OnlinePreTranslationService(
                    database = db,
                    appDataStore = appDataStore,
                    aiCompletionService = AiCompletionService(OpenAIService(), TestOnDeviceAI()),
                    coroutineScope = CoroutineScope(Dispatchers.Unconfined),
                )
            val accountKey = MicroBlogKey(id = "account-same-language", host = "test.social")
            val post =
                createPost(
                    accountType = AccountType.Specific(accountKey),
                    user = profile(MicroBlogKey("user-same-language", "test.social"), "User"),
                    statusKey = MicroBlogKey("status-same-language", "test.social"),
                    text = "已经是中文",
                ).copy(
                    sourceLanguages = persistentListOf(Locale.language),
                )
            val loader =
                FakeLoader("home") { request ->
                    when (request) {
                        PagingRequest.Refresh ->
                            PagingResult(
                                data = listOf(post),
                                nextKey = null,
                            )

                        is PagingRequest.Append -> error("No append expected")
                        is PagingRequest.Prepend -> error("No prepend expected")
                    }
                }
            val mediator =
                TimelineRemoteMediator(
                    loader = loader,
                    database = db,
                    allowLongText = false,
                    preTranslationService = preTranslationService,
                )

            val mediatorResult =
                mediator.load(
                    loadType = LoadType.REFRESH,
                    state =
                        PagingState(
                            pages = emptyList(),
                            anchorPosition = null,
                            config = PagingConfig(pageSize = 20),
                            leadingPlaceholderCount = 0,
                        ),
                )
            assertTrue(mediatorResult is androidx.paging.RemoteMediator.MediatorResult.Success)

            val savedStatus = db.statusDao().get(post.statusKey, AccountType.Specific(accountKey)).first()
            assertNotNull(savedStatus)
            val translation =
                withTimeout(5_000) {
                    db
                        .translationDao()
                        .find(
                            entityType = TranslationEntityType.Status,
                            entityKey = savedStatus.id,
                            targetLanguage = Locale.language,
                        ).filterNotNull()
                        .first()
                }
            assertEquals(TranslationStatus.Skipped, translation.status)
            assertEquals("source_language_matches_target", translation.statusReason)
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun homeTimelineAcceptsAiSkippedTranslationResult() =
        runTest {
            val appDataStore = AppDataStore(pathProducer)
            appDataStore.appSettingsStore.updateData {
                it.copy(
                    language = Locale.language,
                    translateConfig = aiPreTranslateConfig(),
                    aiConfig =
                        AppSettings.AiConfig(
                            type = AppSettings.AiConfig.Type.OnDevice,
                        ),
                )
            }
            val preTranslationService: PreTranslationService =
                OnlinePreTranslationService(
                    database = db,
                    appDataStore = appDataStore,
                    aiCompletionService = AiCompletionService(OpenAIService(), SkippingOnDeviceAI()),
                    coroutineScope = backgroundScope,
                )
            val accountKey = MicroBlogKey(id = "account-ai-skipped", host = "test.social")
            val post =
                createPost(
                    accountType = AccountType.Specific(accountKey),
                    user = profile(MicroBlogKey("user-ai-skipped", "test.social"), "User"),
                    statusKey = MicroBlogKey("status-ai-skipped", "test.social"),
                    text = "already target language",
                )
            val loader =
                FakeLoader("home") { request ->
                    when (request) {
                        PagingRequest.Refresh ->
                            PagingResult(
                                data = listOf(post),
                                nextKey = null,
                            )

                        is PagingRequest.Append -> error("No append expected")
                        is PagingRequest.Prepend -> error("No prepend expected")
                    }
                }
            val mediator =
                TimelineRemoteMediator(
                    loader = loader,
                    database = db,
                    allowLongText = false,
                    preTranslationService = preTranslationService,
                )

            val mediatorResult =
                mediator.load(
                    loadType = LoadType.REFRESH,
                    state =
                        PagingState(
                            pages = emptyList(),
                            anchorPosition = null,
                            config = PagingConfig(pageSize = 20),
                            leadingPlaceholderCount = 0,
                        ),
                )
            assertTrue(mediatorResult is androidx.paging.RemoteMediator.MediatorResult.Success)
            advanceUntilIdle()

            val savedStatus = db.statusDao().get(post.statusKey, AccountType.Specific(accountKey)).first()
            assertNotNull(savedStatus)
            val translation =
                db.translationDao().get(
                    entityType = TranslationEntityType.Status,
                    entityKey = savedStatus.id,
                    targetLanguage = Locale.language,
                )
            assertNotNull(translation)
            assertEquals(TranslationStatus.Skipped, translation.status)
            assertEquals("same_language", translation.statusReason)
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun homeTimelineSkipsPreTranslationForNonTranslatableOnlyPosts() =
        runTest {
            val appDataStore = AppDataStore(pathProducer)
            appDataStore.appSettingsStore.updateData {
                it.copy(
                    language = Locale.language,
                    translateConfig = aiPreTranslateConfig(),
                    aiConfig =
                        AppSettings.AiConfig(
                            type = AppSettings.AiConfig.Type.OnDevice,
                        ),
                )
            }
            val preTranslationService: PreTranslationService =
                OnlinePreTranslationService(
                    database = db,
                    appDataStore = appDataStore,
                    aiCompletionService = AiCompletionService(OpenAIService(), TestOnDeviceAI()),
                    coroutineScope = CoroutineScope(Dispatchers.Unconfined),
                )
            val accountKey = MicroBlogKey(id = "account-emoji-only", host = "test.social")
            val post =
                createPost(
                    accountType = AccountType.Specific(accountKey),
                    user = profile(MicroBlogKey("user-emoji-only", "test.social"), "User"),
                    statusKey = MicroBlogKey("status-emoji-only", "test.social"),
                    text = "😀🎉✨   #tag https://example.com",
                )
            val loader =
                FakeLoader("home") { request ->
                    when (request) {
                        PagingRequest.Refresh ->
                            PagingResult(
                                data = listOf(post),
                                nextKey = null,
                            )

                        is PagingRequest.Append -> error("No append expected")
                        is PagingRequest.Prepend -> error("No prepend expected")
                    }
                }
            val mediator =
                TimelineRemoteMediator(
                    loader = loader,
                    database = db,
                    allowLongText = false,
                    preTranslationService = preTranslationService,
                )

            val mediatorResult =
                mediator.load(
                    loadType = LoadType.REFRESH,
                    state =
                        PagingState(
                            pages = emptyList(),
                            anchorPosition = null,
                            config = PagingConfig(pageSize = 20),
                            leadingPlaceholderCount = 0,
                        ),
                )
            assertTrue(mediatorResult is androidx.paging.RemoteMediator.MediatorResult.Success)

            val savedStatus = db.statusDao().get(post.statusKey, AccountType.Specific(accountKey)).first()
            assertNotNull(savedStatus)
            val translation =
                db
                    .translationDao()
                    .find(
                        entityType = TranslationEntityType.Status,
                        entityKey = savedStatus.id,
                        targetLanguage = Locale.language,
                    ).filterNotNull()
                    .first()
            assertEquals(TranslationStatus.Skipped, translation.status)
            assertEquals("non_translatable_only", translation.statusReason)
        }

    @Test
    fun preTranslationBatchDocumentAllowsMissingTargetLanguageInResponse() {
        val document =
            """{"version":1,"items":[]}""".decodeJson(
                dev.dimension.flare.data.translation.PreTranslationBatchDocument
                    .serializer(),
            )

        assertEquals("", document.targetLanguage)
        assertTrue(document.items.isEmpty())
    }

    @Test
    fun preTranslationServiceMarksStaleInFlightTranslationsAsFailedOnStartup() {
        runBlocking {
            db.translationDao().insert(
                dev.dimension.flare.data.database.cache.model.DbTranslation(
                    entityType = TranslationEntityType.Status,
                    entityKey = "status:stale-in-flight",
                    targetLanguage = Locale.language,
                    sourceHash = "hash-stale",
                    status = TranslationStatus.Translating,
                    updatedAt = 1L,
                ),
            )

            val appDataStore = AppDataStore(pathProducer)
            appDataStore.appSettingsStore.updateData {
                it.copy(
                    language = Locale.language,
                    translateConfig = aiPreTranslateConfig(),
                    aiConfig =
                        AppSettings.AiConfig(
                            type = AppSettings.AiConfig.Type.OnDevice,
                        ),
                )
            }
            val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

            OnlinePreTranslationService(
                database = db,
                appDataStore = appDataStore,
                aiCompletionService = AiCompletionService(OpenAIService(), TestOnDeviceAI()),
                coroutineScope = scope,
            )

            yield()

            val cleaned =
                db.translationDao().get(
                    entityType = TranslationEntityType.Status,
                    entityKey = "status:stale-in-flight",
                    targetLanguage = Locale.language,
                )
            assertNotNull(cleaned)
            assertEquals(TranslationStatus.Failed, cleaned.status)
            assertEquals("stale_in_flight", cleaned.statusReason)

            scope.coroutineContext[Job]?.cancel()
        }
    }

    @Test
    fun queuedPreTranslationWritesPendingBeforeExecutionStarts() {
        runBlocking {
            val appDataStore = AppDataStore(pathProducer)
            appDataStore.appSettingsStore.updateData {
                it.copy(
                    language = Locale.language,
                    translateConfig = aiPreTranslateConfig(),
                    aiConfig =
                        AppSettings.AiConfig(
                            type = AppSettings.AiConfig.Type.OnDevice,
                        ),
                )
            }
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
            val preTranslationService: PreTranslationService =
                OnlinePreTranslationService(
                    database = db,
                    appDataStore = appDataStore,
                    aiCompletionService = AiCompletionService(OpenAIService(), BlockingOnDeviceAI(started, release)),
                    coroutineScope = scope,
                )
            try {
                val accountKey = MicroBlogKey(id = "account-pending-queue", host = "test.social")
                val accountType = AccountType.Specific(accountKey)
                val firstStatus =
                    TimelinePagingMapper
                        .toDb(
                            createPost(
                                accountType = accountType,
                                user = profile(MicroBlogKey("user-pending-1", "test.social"), "User"),
                                statusKey = MicroBlogKey("status-pending-1", "test.social"),
                                text = "first source",
                            ),
                            pagingKey = "home",
                        ).status.status.data
                val secondStatus =
                    TimelinePagingMapper
                        .toDb(
                            createPost(
                                accountType = accountType,
                                user = profile(MicroBlogKey("user-pending-2", "test.social"), "User"),
                                statusKey = MicroBlogKey("status-pending-2", "test.social"),
                                text = "second source",
                            ),
                            pagingKey = "home",
                        ).status.status.data

                preTranslationService.enqueueStatuses(listOf(firstStatus), allowLongText = false)
                withTimeout(5_000) {
                    started.await()
                }

                preTranslationService.enqueueStatuses(listOf(secondStatus), allowLongText = false)

                val pendingTranslation =
                    withTimeout(5_000) {
                        db
                            .translationDao()
                            .find(
                                entityType = TranslationEntityType.Status,
                                entityKey = secondStatus.id,
                                targetLanguage = Locale.language,
                            ).filterNotNull()
                            .first { it.status == TranslationStatus.Pending }
                    }
                assertEquals(TranslationStatus.Pending, pendingTranslation.status)
            } finally {
                release.complete(Unit)
                scope.coroutineContext[Job]?.cancelAndJoin()
            }
        }
    }

    @Test
    fun refreshDeduplicatesSamePostReturnedByMultipleSubTimelines() =
        runTest {
            val accountKey = MicroBlogKey("timeline", "mastodon.example")
            val accountType = AccountType.Specific(accountKey)
            val user = profile(MicroBlogKey("user", "mastodon.example"), "User")
            val duplicatedPost =
                createPost(
                    accountType = accountType,
                    user = user,
                    statusKey = MicroBlogKey("same", "mastodon.example"),
                    text = "duplicate",
                )

            val first =
                FakeLoader("home") { request ->
                    when (request) {
                        PagingRequest.Refresh ->
                            PagingResult(
                                data = listOf(duplicatedPost),
                                nextKey = null,
                            )

                        is PagingRequest.Append -> error("No append expected")
                        is PagingRequest.Prepend -> error("No prepend expected")
                    }
                }
            val second =
                FakeLoader("list") { request ->
                    when (request) {
                        PagingRequest.Refresh ->
                            PagingResult(
                                data = listOf(duplicatedPost),
                                nextKey = null,
                            )

                        is PagingRequest.Append -> error("No append expected")
                        is PagingRequest.Prepend -> error("No prepend expected")
                    }
                }

            val mediator = MixedRemoteMediator(db, listOf(first, second))
            val result = mediator.load(pageSize = 20, request = PagingRequest.Refresh)

            assertEquals(1, result.data.size)
            assertEquals(duplicatedPost.itemKey, result.data.single().itemKey)
        }

    @Test
    fun refreshKeepsSamePostFromDifferentAccountsAsSeparateItems() =
        runTest {
            val firstAccount = AccountType.Specific(MicroBlogKey("timeline-a", "mastodon.example"))
            val secondAccount = AccountType.Specific(MicroBlogKey("timeline-b", "mastodon.example"))
            val sharedStatusKey = MicroBlogKey("same", "mastodon.example")

            val firstPost =
                createPost(
                    accountType = firstAccount,
                    user = profile(MicroBlogKey("user-a", "mastodon.example"), "User A"),
                    statusKey = sharedStatusKey,
                    text = "duplicate",
                )
            val secondPost =
                createPost(
                    accountType = secondAccount,
                    user = profile(MicroBlogKey("user-b", "mastodon.example"), "User B"),
                    statusKey = sharedStatusKey,
                    text = "duplicate",
                )

            val first =
                FakeLoader("home_a") { request ->
                    when (request) {
                        PagingRequest.Refresh ->
                            PagingResult(
                                data = listOf(firstPost),
                                nextKey = null,
                            )

                        is PagingRequest.Append -> error("No append expected")
                        is PagingRequest.Prepend -> error("No prepend expected")
                    }
                }
            val second =
                FakeLoader("home_b") { request ->
                    when (request) {
                        PagingRequest.Refresh ->
                            PagingResult(
                                data = listOf(secondPost),
                                nextKey = null,
                            )

                        is PagingRequest.Append -> error("No append expected")
                        is PagingRequest.Prepend -> error("No prepend expected")
                    }
                }

            val mediator = MixedRemoteMediator(db, listOf(first, second))
            val result = mediator.load(pageSize = 20, request = PagingRequest.Refresh)

            assertEquals(2, result.data.size)
            assertEquals(
                setOf(firstPost.itemKey, secondPost.itemKey),
                result.data.map { it.itemKey }.toSet(),
            )
        }

    private class FakeLoader(
        override val pagingKey: String,
        private val onLoad: suspend (PagingRequest) -> PagingResult<UiTimelineV2>,
    ) : CacheableRemoteLoader<UiTimelineV2> {
        val requests = mutableListOf<PagingRequest>()

        override suspend fun load(
            pageSize: Int,
            request: PagingRequest,
        ): PagingResult<UiTimelineV2> {
            requests += request
            return onLoad(request)
        }
    }

    private fun feed(
        url: String,
        createdAtEpochMs: Long,
    ) = UiTimelineV2.Feed(
        title = url.substringAfterLast('/'),
        description = null,
        url = url,
        createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs).toUi(),
        source =
            UiTimelineV2.Feed.Source(
                name = "test",
                icon = null,
            ),
        openInBrowser = false,
        accountType = AccountType.Guest,
    )

    private fun profile(
        key: MicroBlogKey,
        name: String,
    ): UiProfile =
        UiProfile(
            key = key,
            handle =
                UiHandle(
                    raw = key.id,
                    host = key.host,
                ),
            avatar = "https://${key.host}/${key.id}.png",
            nameInternal = name.toUiPlainText(),
            platformType = PlatformType.Mastodon,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(fansCount = 0, followsCount = 0, statusesCount = 0, platformFansCount = "0"),
            mark = persistentListOf(),
            bottomContent = null,
        )

    private fun createPost(
        accountType: AccountType,
        user: UiProfile,
        statusKey: MicroBlogKey,
        text: String,
        parents: List<UiTimelineV2.Post> = emptyList(),
    ): UiTimelineV2.Post =
        UiTimelineV2.Post(
            message = null,
            platformType = PlatformType.Mastodon,
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = user,
            quote = persistentListOf(),
            content = text.toUiPlainText(),
            actions = persistentListOf(),
            poll = null,
            statusKey = statusKey,
            card = null,
            createdAt = Clock.System.now().toUi(),
            emojiReactions = persistentListOf(),
            sourceChannel = null,
            visibility = null,
            replyToHandle = null,
            references = persistentListOf(),
            parents = parents.toPersistentList(),
            clickEvent = ClickEvent.Noop,
            accountType = accountType,
        )
}

private class TestOnDeviceAI : OnDeviceAI {
    override suspend fun isAvailable(): Boolean = true

    override suspend fun translate(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? {
        val document =
            source.decodeJson(
                dev.dimension.flare.data.translation.PreTranslationBatchDocument
                    .serializer(),
            )
        return document
            .copy(
                items =
                    document.items.map { item ->
                        item.copy(
                            status = dev.dimension.flare.data.translation.PreTranslationBatchItemStatus.Completed,
                            payload = requireNotNull(item.payload).translated(targetLanguage),
                            reason = null,
                        )
                    },
            ).encodeJson(
                dev.dimension.flare.data.translation.PreTranslationBatchDocument
                    .serializer(),
            )
    }

    override suspend fun tldr(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? = null
}

private class BlockingOnDeviceAI(
    private val started: CompletableDeferred<Unit>,
    private val release: CompletableDeferred<Unit>,
) : OnDeviceAI {
    override suspend fun isAvailable(): Boolean = true

    override suspend fun translate(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? {
        started.complete(Unit)
        release.await()
        val document =
            source.decodeJson(
                dev.dimension.flare.data.translation.PreTranslationBatchDocument
                    .serializer(),
            )
        return document
            .copy(
                items =
                    document.items.map { item ->
                        item.copy(
                            status = dev.dimension.flare.data.translation.PreTranslationBatchItemStatus.Completed,
                            payload = requireNotNull(item.payload).translated(targetLanguage),
                            reason = null,
                        )
                    },
            ).encodeJson(
                dev.dimension.flare.data.translation.PreTranslationBatchDocument
                    .serializer(),
            )
    }

    override suspend fun tldr(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? = null
}

private class SkippingOnDeviceAI : OnDeviceAI {
    override suspend fun isAvailable(): Boolean = true

    override suspend fun translate(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? {
        val document =
            source.decodeJson(
                dev.dimension.flare.data.translation.PreTranslationBatchDocument
                    .serializer(),
            )
        return document
            .copy(
                items =
                    document.items.map { item ->
                        item.copy(
                            status = dev.dimension.flare.data.translation.PreTranslationBatchItemStatus.Skipped,
                            payload = null,
                            reason = "same_language",
                        )
                    },
            ).encodeJson(
                dev.dimension.flare.data.translation.PreTranslationBatchDocument
                    .serializer(),
            )
    }

    override suspend fun tldr(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? = null
}

private fun dev.dimension.flare.data.translation.PreTranslationBatchPayload.translated(
    targetLanguage: String,
): dev.dimension.flare.data.translation.PreTranslationBatchPayload =
    dev.dimension.flare.data.translation.PreTranslationBatchPayload(
        content = content?.translated(targetLanguage),
        contentWarning = contentWarning?.translated(targetLanguage),
        title = title?.translated(targetLanguage),
        description = description?.translated(targetLanguage),
    )

private fun TranslationDocument.translated(targetLanguage: String): TranslationDocument =
    copy(
        blocks =
            blocks.map { block ->
                block.copy(
                    tokens =
                        block.tokens.map { token ->
                            if (token.kind == TranslationTokenKind.Translatable) {
                                token.copy(text = "${token.text} ($targetLanguage)")
                            } else {
                                token
                            }
                        },
                )
            },
    )
