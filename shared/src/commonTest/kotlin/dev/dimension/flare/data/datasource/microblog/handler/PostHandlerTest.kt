package dev.dimension.flare.data.datasource.microblog.handler

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.CacheState
import dev.dimension.flare.common.Locale
import dev.dimension.flare.common.OnDeviceAI
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusReference
import dev.dimension.flare.data.database.cache.model.TranslationEntityType
import dev.dimension.flare.data.database.cache.model.TranslationStatus
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
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
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.TranslationDocument
import dev.dimension.flare.ui.render.TranslationTokenKind
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class PostHandlerTest : RobolectricTest() {
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
    private lateinit var appDataStore: AppDataStore
    private lateinit var fakeLoader: FakePostLoader
    private lateinit var onDeviceAI: FakePostOnDeviceAI

    private val accountKey = MicroBlogKey(id = "user-1", host = "test.social")
    private val accountType = AccountType.Specific(accountKey)
    private val postKey = MicroBlogKey(id = "post-1", host = "test.social")

    @BeforeTest
    fun setup() {
        db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()

        appDataStore = AppDataStore(pathProducer)
        fakeLoader = FakePostLoader()
        onDeviceAI = FakePostOnDeviceAI()
    }

    @AfterTest
    fun tearDown() {
        db.close()
        stopKoin()
        deleteTestRootPath(root)
    }

    private fun startTestKoin(scope: CoroutineScope) {
        startKoin {
            modules(
                module {
                    single { db }
                    single { appDataStore }
                    single<CoroutineScope> { scope }
                    single<OnDeviceAI> { onDeviceAI }
                    single { OpenAIService() }
                    single { AiCompletionService(get(), get()) }
                    single<PreTranslationService> { OnlinePreTranslationService(get(), get(), get(), get()) }
                    single<PlatformFormatter> { TestFormatter() }
                },
            )
        }
    }

    @Test
    fun postRefreshFetchesAndStoresInDatabase() =
        runTest {
            startTestKoin(this@runTest)

            val expected = createPost(statusKey = postKey)
            fakeLoader.nextStatus = expected
            val handler = PostHandler(accountType = accountType, loader = fakeLoader)
            val cacheable = handler.post(postKey)

            val cachedValueDeferred =
                async {
                    cacheable.data
                        .filterIsInstance<CacheState.Success<UiTimelineV2>>()
                        .first()
                        .data
                }

            val refreshState = cacheable.refreshState.drop(1).first()
            assertTrue(refreshState is androidx.paging.LoadState.NotLoading)

            val cached = cachedValueDeferred.await()
            assertEquals(postKey, cached.statusKey)
            assertEquals(1, fakeLoader.statusCallCount)

            val savedStatus = db.statusDao().get(postKey, accountType).first()
            assertNotNull(savedStatus)
            assertEquals(postKey, savedStatus.statusKey)
            val pagingExists = db.pagingTimelineDao().existsPaging(accountKey, "post_only_$postKey")
            assertTrue(pagingExists)
        }

    @Test
    fun postUsesLocalStatusCacheBeforeRefreshCreatesPagingRow() =
        runTest {
            startTestKoin(this@runTest)

            val local = createPost(statusKey = postKey)
            db.statusDao().insert(
                DbStatus(
                    statusKey = postKey,
                    accountType = accountType,
                    content = local,
                    text = "text",
                ),
            )

            val handler = PostHandler(accountType = accountType, loader = fakeLoader)
            val cacheable = handler.post(postKey)

            val cached =
                cacheable.data
                    .filterIsInstance<CacheState.Success<UiTimelineV2>>()
                    .first()
                    .data

            assertEquals(postKey, cached.statusKey)
            assertEquals(0, fakeLoader.statusCallCount)
        }

    @Test
    fun postUsesInnerRepostWhenOnlyLocalStatusCacheExists() =
        runTest {
            startTestKoin(this@runTest)

            val repostKey = MicroBlogKey(id = "repost-1", host = "test.social")
            val repost = createPost(statusKey = repostKey)
            val wrapper =
                createPost(statusKey = postKey).copy(
                    content = "wrapper content".toUiPlainText(),
                    internalRepost = repost,
                )

            saveToDatabase(
                db,
                listOf(
                    TimelinePagingMapper.toDb(
                        wrapper,
                        pagingKey = "post_only_$postKey",
                    ),
                ),
            )
            db.pagingTimelineDao().delete("post_only_$postKey")

            val handler = PostHandler(accountType = accountType, loader = fakeLoader)
            val cacheable = handler.post(postKey)

            val cached =
                cacheable.data
                    .filterIsInstance<CacheState.Success<UiTimelineV2>>()
                    .first()
                    .data

            val cachedPost = assertNotNull(cached as? UiTimelineV2.Post)
            val internalRepost = assertNotNull(cachedPost.internalRepost)
            assertEquals(postKey, cachedPost.statusKey)
            assertEquals(repostKey, internalRepost.statusKey)
            assertEquals(repost.content.raw, cachedPost.content.raw)
            assertEquals(repost.user?.key, cachedPost.user?.key)
            assertEquals(0, fakeLoader.statusCallCount)
        }

    @Test
    fun postRefreshKeepsLocalParentsWhenRemoteParentsIsEmpty() =
        runTest {
            startTestKoin(this@runTest)

            val parentKey = MicroBlogKey(id = "parent-1", host = "test.social")
            val localWithParents =
                createPost(statusKey = postKey).copy(
                    references =
                        persistentListOf(
                            UiTimelineV2.Post.Reference(
                                statusKey = parentKey,
                                type = ReferenceType.Reply,
                            ),
                        ),
                )
            db.statusDao().insert(
                DbStatus(
                    statusKey = postKey,
                    accountType = accountType,
                    content = localWithParents,
                    text = "text",
                ),
            )
            fakeLoader.nextStatus = createPost(statusKey = postKey, parents = persistentListOf())

            val handler = PostHandler(accountType = accountType, loader = fakeLoader)
            val cacheable = handler.post(postKey)
            val refreshState = cacheable.refreshState.drop(1).first()
            assertTrue(refreshState is androidx.paging.LoadState.NotLoading)

            val savedStatus = db.statusDao().get(postKey, accountType).first()
            val savedPost = savedStatus?.content as? UiTimelineV2.Post
            assertNotNull(savedPost)
            assertEquals(1, savedPost.references.size)
            assertEquals(parentKey, savedPost.references.first().statusKey)
        }

    @Test
    fun postRefreshUsesRemoteParentsWhenRemoteParentsIsNotEmpty() =
        runTest {
            startTestKoin(this@runTest)

            val localParentKey = MicroBlogKey(id = "local-parent", host = "test.social")
            val remoteParentKey = MicroBlogKey(id = "remote-parent", host = "test.social")
            val local =
                createPost(statusKey = postKey).copy(
                    references =
                        persistentListOf(
                            UiTimelineV2.Post.Reference(
                                statusKey = localParentKey,
                                type = ReferenceType.Reply,
                            ),
                        ),
                )
            db.statusDao().insert(
                DbStatus(
                    statusKey = postKey,
                    accountType = accountType,
                    content = local,
                    text = "text",
                ),
            )
            fakeLoader.nextStatus = createPost(statusKey = postKey, parents = persistentListOf(createPost(statusKey = remoteParentKey)))

            val handler = PostHandler(accountType = accountType, loader = fakeLoader)
            val cacheable = handler.post(postKey)
            val refreshState = cacheable.refreshState.drop(1).first()
            assertTrue(refreshState is androidx.paging.LoadState.NotLoading)

            val savedStatus = db.statusDao().get(postKey, accountType).first()
            val savedPost = savedStatus?.content as? UiTimelineV2.Post
            assertNotNull(savedPost)
            assertEquals(1, savedPost.references.size)
            assertEquals(remoteParentKey, savedPost.references.first().statusKey)
        }

    @Test
    fun deleteSuccessRemovesStatusReferencesAndPaging() =
        runTest {
            startTestKoin(this@runTest)

            db.statusDao().insert(
                DbStatus(
                    statusKey = postKey,
                    accountType = accountType,
                    content = createPost(statusKey = postKey),
                    text = "text",
                ),
            )
            db.pagingTimelineDao().insertAll(
                listOf(
                    DbPagingTimeline(
                        pagingKey = "post_only_$postKey",
                        statusKey = postKey,
                        sortId = 1L,
                    ),
                ),
            )
            db.statusReferenceDao().insertAll(
                listOf(
                    DbStatusReference(
                        _id = Uuid.random().toString(),
                        referenceType = dev.dimension.flare.model.ReferenceType.Reply,
                        statusId = DbStatus.createId(accountType, postKey),
                        referenceStatusId = DbStatus.createId(accountType, MicroBlogKey("ref-1", postKey.host)),
                    ),
                ),
            )

            val handler = PostHandler(accountType = accountType, loader = fakeLoader)
            handler.delete(postKey)
            advanceUntilIdle()

            assertEquals(1, fakeLoader.deleteCallCount)
            val savedStatus = db.statusDao().get(postKey, accountType).first()
            assertNull(savedStatus)
            val refs = db.statusReferenceDao().getByStatusId(DbStatus.createId(accountType, postKey))
            assertTrue(refs.isEmpty())
            val pagingRows = db.pagingTimelineDao().getByPagingKeyAndStatusKeys("post_only_$postKey", listOf(postKey))
            assertTrue(pagingRows.isEmpty())
        }

    @Test
    fun deleteFailureKeepsLocalCache() =
        runTest {
            startTestKoin(this@runTest)

            db.statusDao().insert(
                DbStatus(
                    statusKey = postKey,
                    accountType = accountType,
                    content = createPost(statusKey = postKey),
                    text = "text",
                ),
            )
            db.pagingTimelineDao().insertAll(
                listOf(
                    DbPagingTimeline(
                        pagingKey = "post_only_$postKey",
                        statusKey = postKey,
                        sortId = 1L,
                    ),
                ),
            )

            fakeLoader.shouldFailDelete = true
            val handler = PostHandler(accountType = accountType, loader = fakeLoader)
            handler.delete(postKey)
            advanceUntilIdle()

            assertEquals(1, fakeLoader.deleteCallCount)
            val savedStatus = db.statusDao().get(postKey, accountType).first()
            assertNotNull(savedStatus)
            val pagingExists = db.pagingTimelineDao().existsPaging(accountKey, "post_only_$postKey")
            assertTrue(pagingExists)
        }

    @Test
    fun postRefreshPreTranslatesLongTextWhenOpenedInDetail() =
        runTest {
            startTestKoin(this@runTest)
            appDataStore.appSettingsStore.updateData {
                it.copy(
                    language = "zh-CN",
                    translateConfig = aiPreTranslateConfig(),
                    aiConfig =
                        AppSettings.AiConfig(
                            type = AppSettings.AiConfig.Type.OnDevice,
                        ),
                )
            }

            val longText = buildString { repeat(520) { append('长') } }
            fakeLoader.nextStatus = createPost(statusKey = postKey, text = longText)
            val handler = PostHandler(accountType = accountType, loader = fakeLoader)
            val cacheable = handler.post(postKey)

            val refreshState = cacheable.refreshState.drop(1).first()
            assertTrue(refreshState is androidx.paging.LoadState.NotLoading)

            val savedStatus = db.statusDao().get(postKey, accountType).first()
            assertNotNull(savedStatus)
            val translation =
                db
                    .translationDao()
                    .find(
                        entityType = TranslationEntityType.Status,
                        entityKey = savedStatus.id,
                        targetLanguage = Locale.language,
                    ).filterNotNull()
                    .first { it.status == TranslationStatus.Completed }
            assertEquals("$longText (${Locale.language})", translation.payload?.content?.raw)

            val translated =
                cacheable.data
                    .filterIsInstance<CacheState.Success<UiTimelineV2>>()
                    .first { (it.data as? UiTimelineV2.Post)?.content?.raw == "$longText (${Locale.language})" }
                    .data as UiTimelineV2.Post
            assertEquals("$longText (${Locale.language})", translated.content.raw)
        }

    private fun createPost(
        statusKey: MicroBlogKey,
        parents: PersistentList<UiTimelineV2.Post> = persistentListOf(),
        text: String = "post content",
    ): UiTimelineV2.Post =
        UiTimelineV2.Post(
            message = null,
            platformType = PlatformType.Mastodon,
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = null,
            quote = persistentListOf(),
            content = text.toUiPlainText(),
            actions = persistentListOf(),
            poll = null,
            statusKey = statusKey,
            card = null,
            createdAt =
                kotlin.time.Clock.System
                    .now()
                    .toUi(),
            emojiReactions = persistentListOf(),
            sourceChannel = null,
            visibility = null,
            replyToHandle = null,
            references = persistentListOf(),
            parents = parents,
            clickEvent = ClickEvent.Noop,
            accountType = accountType,
        )

    private class FakePostLoader : PostLoader {
        var nextStatus: UiTimelineV2? = null
        var shouldFailDelete: Boolean = false
        var statusCallCount: Int = 0
        var deleteCallCount: Int = 0

        override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 {
            statusCallCount++
            return requireNotNull(nextStatus)
        }

        override suspend fun deleteStatus(statusKey: MicroBlogKey) {
            deleteCallCount++
            if (shouldFailDelete) {
                error("delete failed")
            }
        }
    }
}

private class FakePostOnDeviceAI : OnDeviceAI {
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
