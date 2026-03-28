package dev.dimension.flare.data.datasource.microblog

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.OnDeviceAI
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.cache.CacheDatabase
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
import dev.dimension.flare.data.translation.AiPreTranslationService
import dev.dimension.flare.data.translation.PreTranslationService
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
            val timelineRemoteMediator = TimelineRemoteMediator(loader = mixed, database = db)

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
                    (it.status.status.data.content as? UiTimelineV2.Feed)?.url
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

            val mediator = TimelineRemoteMediator(loader = loader, database = db)
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
                    language = "zh-CN",
                    aiConfig =
                        AppSettings.AiConfig(
                            translation = true,
                            preTranslation = true,
                            type = AppSettings.AiConfig.Type.OnDevice,
                        ),
                )
            }
            val preTranslationService: PreTranslationService =
                AiPreTranslationService(
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
                        targetLanguage = "zh-CN",
                    ).filterNotNull()
                    .first { it.status == TranslationStatus.Completed }
            val parentTranslation =
                db
                    .translationDao()
                    .find(
                        entityType = TranslationEntityType.Status,
                        entityKey = savedParent.id,
                        targetLanguage = "zh-CN",
                    ).filterNotNull()
                    .first { it.status == TranslationStatus.Completed }

            assertEquals("root source (zh-CN)", rootTranslation.payload?.content?.raw)
            assertEquals("parent source (zh-CN)", parentTranslation.payload?.content?.raw)
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
                            payload = item.payload.translated(targetLanguage),
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
