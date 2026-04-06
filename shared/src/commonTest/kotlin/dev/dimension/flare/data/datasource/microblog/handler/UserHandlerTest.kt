package dev.dimension.flare.data.datasource.microblog.handler

import androidx.paging.LoadState
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.CacheState
import dev.dimension.flare.common.Locale
import dev.dimension.flare.common.OnDeviceAI
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbTranslation
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.TranslationEntityType
import dev.dimension.flare.data.database.cache.model.TranslationPayload
import dev.dimension.flare.data.database.cache.model.TranslationStatus
import dev.dimension.flare.data.database.cache.model.sourceHash
import dev.dimension.flare.data.database.cache.model.translationEntityKey
import dev.dimension.flare.data.database.cache.model.translationPayload
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.network.ai.AiCompletionService
import dev.dimension.flare.data.network.ai.OpenAIService
import dev.dimension.flare.data.translation.OnlinePreTranslationService
import dev.dimension.flare.data.translation.PreTranslationBatchDocument
import dev.dimension.flare.data.translation.PreTranslationBatchPayload
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.data.translation.aiPreTranslateConfig
import dev.dimension.flare.data.translation.cacheKey
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.render.TranslationDocument
import dev.dimension.flare.ui.render.TranslationTokenKind
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UserHandlerTest : RobolectricTest() {
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
    private lateinit var loader: FakeUserLoader
    private lateinit var handler: UserHandler
    private lateinit var onDeviceAI: FakeOnDeviceAI

    private val accountKey = MicroBlogKey(id = "account-1", host = "test.social")
    private val aiTranslationProviderCacheKey =
        AppSettings.TranslateConfig.Provider.AI
            .cacheKey()

    @BeforeTest
    fun setup() {
        db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        appDataStore = AppDataStore(pathProducer)

        loader = FakeUserLoader()
        onDeviceAI = FakeOnDeviceAI()

        startKoin {
            modules(
                module {
                    single { db }
                    single { appDataStore }
                    single<CoroutineScope> { CoroutineScope(Dispatchers.Unconfined) }
                    single<OnDeviceAI> { onDeviceAI }
                    single { OpenAIService() }
                    single { AiCompletionService(get(), get()) }
                    single<PreTranslationService> { OnlinePreTranslationService(get(), get(), get(), get()) }
                    single<PlatformFormatter> { TestFormatter() }
                },
            )
        }

        handler = UserHandler(host = accountKey.host, loader = loader)
    }

    @AfterTest
    fun tearDown() {
        db.close()
        stopKoin()
        deleteTestRootPath(root)
    }

    @Test
    fun userByHandleAndHostRefreshStoresAndEmitsUser() =
        runTest {
            val expected = createProfile(id = "alice", host = "test.social", handle = "alice")
            loader.nextByHandleAndHost = expected

            val cacheable =
                handler.userByHandleAndHost(
                    UiHandle(
                        raw = "alice",
                        host = "test.social",
                    ),
                )
            val valueDeferred =
                async {
                    cacheable.data
                        .filterIsInstance<CacheState.Success<UiProfile>>()
                        .first()
                        .data
                }

            val refreshState = cacheable.refreshState.drop(1).first()
            assertTrue(refreshState is LoadState.NotLoading)
            assertEquals(expected.key, valueDeferred.await().key)
            assertEquals(1, loader.byHandleCallCount)

            val saved = db.userDao().findByCanonicalHandleAndHost("@alice@test.social", "test.social").first()
            assertNotNull(saved)
            assertEquals(expected.key, saved.userKey)
        }

    @Test
    fun userByIdRefreshStoresAndEmitsUser() =
        runTest {
            val expected = createProfile(id = "bob", host = "test.social", handle = "@bob@test.social")
            loader.nextById = expected

            val cacheable = handler.userById("bob")
            val valueDeferred =
                async {
                    cacheable.data
                        .filterIsInstance<CacheState.Success<UiProfile>>()
                        .first()
                        .data
                }

            val refreshState = cacheable.refreshState.drop(1).first()
            assertTrue(refreshState is LoadState.NotLoading)
            assertEquals(expected.key, valueDeferred.await().key)
            assertEquals(1, loader.byIdCallCount)

            val saved = db.userDao().findByKey(MicroBlogKey("bob", "test.social")).first()
            assertNotNull(saved)
            assertEquals(expected.key, saved.userKey)
        }

    @Test
    fun refreshFailureKeepsExistingCachedUser() =
        runTest {
            val existing = createProfile(id = "charlie", host = "test.social", handle = "@charlie@test.social")
            db.userDao().insert(
                DbUser(
                    userKey = existing.key,
                    name = existing.name.raw,
                    canonicalHandle = existing.handle.canonical,
                    host = "test.social",
                    content = existing,
                ),
            )
            loader.failById = true

            val cacheable = handler.userById("charlie")
            val refreshState = cacheable.refreshState.drop(1).first()
            assertTrue(refreshState is LoadState.Error)

            val latest =
                cacheable.data
                    .filterIsInstance<CacheState.Success<UiProfile>>()
                    .first()
                    .data
            assertEquals(existing.key, latest.key)
        }

    @Test
    fun uiHandleQueryMatchesAtHandleInCache() =
        runTest {
            val atHandleProfile = createProfile(id = "david", host = "test.social", handle = "@david@test.social")
            db.userDao().insert(
                DbUser(
                    userKey = atHandleProfile.key,
                    name = atHandleProfile.name.raw,
                    canonicalHandle = atHandleProfile.handle.canonical,
                    host = "test.social",
                    content = atHandleProfile,
                ),
            )

            // UserHandler now normalizes raw+host to canonical before querying cache.
            val uiHandleHit = db.userDao().findByCanonicalHandleAndHost("@david@test.social", "test.social").first()
            assertNotNull(uiHandleHit)
            assertEquals(atHandleProfile.key, uiHandleHit.userKey)

            // Stored shape in many loaders/renderers: "@david@test.social"
            val atHandleHit = db.userDao().findByCanonicalHandleAndHost("@david@test.social", "test.social").first()
            assertNotNull(atHandleHit)
            assertEquals(atHandleProfile.key, atHandleHit.userKey)
        }

    @Test
    fun userByIdUsesTranslatedDescriptionWhenPreTranslationEnabled() =
        runTest {
            val profile =
                createProfile(id = "eve", host = "test.social", handle = "@eve@test.social").copy(
                    description = "Original bio".toUiPlainText(),
                )
            db.userDao().insert(
                DbUser(
                    userKey = profile.key,
                    name = profile.name.raw,
                    canonicalHandle = profile.handle.canonical,
                    host = "test.social",
                    content = profile,
                ),
            )
            appDataStore.appSettingsStore.updateData {
                it.copy(
                    language = "zh-CN",
                    translateConfig = aiPreTranslateConfig(),
                    aiConfig =
                        AppSettings.AiConfig(),
                )
            }
            db.translationDao().insert(
                DbTranslation(
                    entityType = TranslationEntityType.Profile,
                    entityKey = profile.translationEntityKey(),
                    targetLanguage = Locale.language,
                    sourceHash = profile.translationPayload().sourceHash(aiTranslationProviderCacheKey),
                    status = TranslationStatus.Completed,
                    payload = TranslationPayload(description = "翻译后的简介".toUiPlainText()),
                    updatedAt = 1L,
                ),
            )

            val cacheable = handler.userById("eve")

            val latest =
                cacheable.data
                    .filterIsInstance<CacheState.Success<UiProfile>>()
                    .first()
                    .data

            assertEquals("翻译后的简介", latest.description?.raw)
        }

    @Test
    fun userByIdRefreshStoresPreTranslationIntoDatabase() =
        runTest {
            val expected =
                createProfile(id = "pretranslate", host = "test.social", handle = "@pretranslate@test.social").copy(
                    description = "Original profile bio".toUiPlainText(),
                )
            loader.nextById = expected
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

            val cacheable = handler.userById("pretranslate")
            val refreshState = cacheable.refreshState.drop(1).first()
            assertTrue(refreshState is LoadState.NotLoading)

            val saved =
                db
                    .translationDao()
                    .find(
                        entityType = TranslationEntityType.Profile,
                        entityKey = expected.translationEntityKey(),
                        targetLanguage = Locale.language,
                    ).filterNotNull()
                    .first { it.status == TranslationStatus.Completed }

            assertEquals("Original profile bio (${Locale.language})", saved.payload?.description?.raw)
        }

    @Test
    fun userByIdRefreshRetriesFailedPreTranslationOnNextLoad() =
        runTest {
            val expected =
                createProfile(id = "retry-translation", host = "test.social", handle = "@retry-translation@test.social").copy(
                    description = "Retry profile bio".toUiPlainText(),
                )
            loader.nextById = expected
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

            onDeviceAI.failTranslation = true
            handler
                .userById("retry-translation")
                .refreshState
                .drop(1)
                .first()

            val failed =
                db
                    .translationDao()
                    .find(
                        entityType = TranslationEntityType.Profile,
                        entityKey = expected.translationEntityKey(),
                        targetLanguage = Locale.language,
                    ).filterNotNull()
                    .first { it.status == TranslationStatus.Failed }
            assertEquals(1, failed.attemptCount)

            onDeviceAI.failTranslation = false
            handler
                .userById("retry-translation")
                .refreshState
                .drop(1)
                .first()

            val completed =
                db
                    .translationDao()
                    .find(
                        entityType = TranslationEntityType.Profile,
                        entityKey = expected.translationEntityKey(),
                        targetLanguage = Locale.language,
                    ).filterNotNull()
                    .first { it.status == TranslationStatus.Completed }
            assertEquals(2, completed.attemptCount)
            assertEquals("Retry profile bio (${Locale.language})", completed.payload?.description?.raw)
        }

    @Test
    fun userByIdRefreshAutoRetriesTransientPreTranslationFailure() =
        runTest {
            val expected =
                createProfile(id = "auto-retry-translation", host = "test.social", handle = "@auto-retry-translation@test.social").copy(
                    description = "Auto retry profile bio".toUiPlainText(),
                )
            loader.nextById = expected
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

            onDeviceAI.remainingTranslationFailures = 1
            handler
                .userById("auto-retry-translation")
                .refreshState
                .drop(1)
                .first()

            val completed =
                db
                    .translationDao()
                    .find(
                        entityType = TranslationEntityType.Profile,
                        entityKey = expected.translationEntityKey(),
                        targetLanguage = Locale.language,
                    ).filterNotNull()
                    .first { it.status == TranslationStatus.Completed }
            assertEquals(1, completed.attemptCount)
            assertEquals(2, onDeviceAI.translationCallCount)
            assertEquals("Auto retry profile bio (${Locale.language})", completed.payload?.description?.raw)
        }

    private fun createProfile(
        id: String,
        host: String,
        handle: String,
    ): UiProfile =
        UiProfile(
            key = MicroBlogKey(id = id, host = host),
            handle =
                UiHandle(
                    raw = handle.removePrefix("@").substringBefore("@"),
                    host = host,
                ),
            avatar = "https://$host/$id.png",
            nameInternal = id.toUiPlainText(),
            platformType = PlatformType.Mastodon,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(fansCount = 0, followsCount = 0, statusesCount = 0, platformFansCount = "0"),
            mark = persistentListOf(),
            bottomContent = null,
        )

    private class FakeUserLoader : UserLoader {
        var nextByHandleAndHost: UiProfile? = null
        var nextById: UiProfile? = null
        var failById: Boolean = false
        var byHandleCallCount: Int = 0
        var byIdCallCount: Int = 0

        override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile {
            byHandleCallCount++
            return requireNotNull(nextByHandleAndHost)
        }

        override suspend fun userById(id: String): UiProfile {
            byIdCallCount++
            if (failById) {
                error("userById failed")
            }
            return requireNotNull(nextById)
        }
    }
}

private class FakeOnDeviceAI : OnDeviceAI {
    var failTranslation: Boolean = false
    var remainingTranslationFailures: Int = 0
    var translationCallCount: Int = 0

    override suspend fun isAvailable(): Boolean = true

    override suspend fun translate(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? {
        translationCallCount++
        if (remainingTranslationFailures > 0) {
            remainingTranslationFailures--
            error("translation failed")
        }
        if (failTranslation) {
            error("translation failed")
        }
        val document = source.decodeJson(PreTranslationBatchDocument.serializer())
        return dev.dimension.flare.data.translation.AiPlaceholderTranslationSupport
            .buildPromptTemplate(
                document.copy(
                    items =
                        document.items.map { item ->
                            item.copy(
                                status = dev.dimension.flare.data.translation.PreTranslationBatchItemStatus.Completed,
                                payload = requireNotNull(item.payload).translated(targetLanguage),
                                reason = null,
                            )
                    },
                ),
            )
    }

    override suspend fun tldr(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? = null
}

private fun PreTranslationBatchPayload.translated(targetLanguage: String): PreTranslationBatchPayload =
    PreTranslationBatchPayload(
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
