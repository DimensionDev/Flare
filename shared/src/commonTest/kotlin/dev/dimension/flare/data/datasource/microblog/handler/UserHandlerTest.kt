package dev.dimension.flare.data.datasource.microblog.handler

import androidx.paging.LoadState
import androidx.room3.Room
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.CacheState
import dev.dimension.flare.common.Locale
import dev.dimension.flare.common.OnDeviceAI
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.mapper.upsertUser
import dev.dimension.flare.data.database.cache.model.DbTranslation
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.TranslationEntityType
import dev.dimension.flare.data.database.cache.model.TranslationPayload
import dev.dimension.flare.data.database.cache.model.TranslationStatus
import dev.dimension.flare.data.database.cache.model.sourceHash
import dev.dimension.flare.data.database.cache.model.translationEntityKey
import dev.dimension.flare.data.database.cache.model.translationPayload
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.network.ai.AiCompletionService
import dev.dimension.flare.data.network.ai.OpenAIService
import dev.dimension.flare.data.translation.OnlinePreTranslationService
import dev.dimension.flare.data.translation.PreTranslationBatchDocument
import dev.dimension.flare.data.translation.PreTranslationBatchPayload
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.data.translation.PreTranslationStoreSupport
import dev.dimension.flare.data.translation.aiPreTranslateConfig
import dev.dimension.flare.data.translation.cacheKey
import dev.dimension.flare.data.translation.canonicalTranslationLanguage
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.di.testSingle
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.render.TranslationDocument
import dev.dimension.flare.ui.render.TranslationTokenKind
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.FileSystem
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

@OptIn(ExperimentalCoroutinesApi::class)
class UserHandlerTest : RobolectricTest() {
    private val root = createTestRootPath()
    private val fileStorage = OkioFileStorage(createTestFileSystem(), root)

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
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        appDataStore = AppDataStore(fileStorage)

        loader = FakeUserLoader()
        onDeviceAI = FakeOnDeviceAI()

        val scope = CoroutineScope(Dispatchers.Unconfined)
        val openAIService = OpenAIService()
        val aiCompletionService = AiCompletionService(openAIService, onDeviceAI)
        startKoin {
            modules(
                module {
                    testSingle { db }
                    testSingle { appDataStore }
                    testSingle<CoroutineScope> { scope }
                    testSingle<OnDeviceAI> { onDeviceAI }
                    testSingle { openAIService }
                    testSingle { aiCompletionService }
                    testSingle<PreTranslationService> {
                        OnlinePreTranslationService(db, appDataStore, aiCompletionService, scope)
                    }
                    testSingle<PlatformFormatter> { TestFormatter() }
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
    fun userByHandleResolvesCanonicalNameAndKeepsObservingUser() =
        runTest {
            val expected = createProfile(id = "2100560390937833472", host = "x.com", handle = "NSJunXiu")
            loader.nextByHandleAndHost = expected
            val cacheable = handler.userByHandleAndHost(UiHandle("nsjunxiu", "x.com"))
            val states = Channel<UiState<UiProfile>>(Channel.UNLIMITED)
            backgroundScope.launch { cacheable.toUi().collect { states.send(it) } }

            val resolved = states.receiveAsFlow().first { it !is UiState.Loading }
            assertEquals(expected, assertIs<UiState.Success<UiProfile>>(resolved).data)
            assertEquals(1, loader.byHandleCallCount)
            assertEquals(0, loader.byIdCallCount)

            val updated = expected.copy(handle = UiHandle("JunRenamed", "x.com"))
            db.upsertUser(updated.toDbUser())

            val changed = states.receiveAsFlow().first { it !is UiState.Loading && it != resolved }
            assertEquals(updated, assertIs<UiState.Success<UiProfile>>(changed).data)
        }

    @Test
    fun userByHandleCacheSwitchPublishesNoErrorThroughCompose() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val expected = createProfile(id = "2100560390937833472", host = "x.com", handle = "NSJunXiu")
                loader.nextByHandleAndHost = expected
                val cacheable = handler.userByHandleAndHost(UiHandle("nsjunxiu", "x.com"))
                val resolved =
                    moleculeFlow(RecompositionMode.Immediate) {
                        cacheable.collectAsState().toUi()
                    }.first { it !is UiState.Loading }

                assertEquals(expected, assertIs<UiState.Success<UiProfile>>(resolved).data)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun userByHandleRefreshFailureKeepsResolvedUser() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val expected = createProfile(id = "2100560390937833472", host = "x.com", handle = "NSJunXiu")
            loader.nextByHandleAndHost = expected
            val cacheable = handler.userByHandleAndHost(UiHandle("nsjunxiu", "x.com"))
            val states = Channel<Pair<LoadState, UiState<UiProfile>>>(Channel.UNLIMITED)
            val job =
                launch {
                    moleculeFlow(RecompositionMode.Immediate) {
                        val state = cacheable.collectAsState()
                        state.refreshState to state.toUi()
                    }.collect { states.send(it) }
                }
            try {
                val resolved = states.receiveAsFlow().first { it.first is LoadState.NotLoading && it.second !is UiState.Loading }
                assertEquals(expected, assertIs<UiState.Success<UiProfile>>(resolved.second).data)

                loader.loadByHandle = { error("user lookup failed") }
                cacheable.refresh()
                val failed = states.receiveAsFlow().first { it.first is LoadState.Error }

                assertEquals(expected, assertIs<UiState.Success<UiProfile>>(failed.second).data)
                assertEquals(2, loader.byHandleCallCount)
            } finally {
                job.cancelAndJoin()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun userByHandleQueriesResolveIndependently() =
        runTest {
            val alice = createProfile(id = "alice-id", host = "test.social", handle = "Alice")
            val bob = createProfile(id = "bob-id", host = "test.social", handle = "Bob")
            val aliceResponse = CompletableDeferred<UiProfile>()
            val bobResponse = CompletableDeferred<UiProfile>()
            loader.loadByHandle = {
                when (it.normalizedRaw) {
                    "alice" -> aliceResponse.await()
                    "bob" -> bobResponse.await()
                    else -> error("Unexpected handle: $it")
                }
            }
            val aliceCache = handler.userByHandleAndHost(UiHandle("alice", "test.social"))
            val bobCache = handler.userByHandleAndHost(UiHandle("bob", "test.social"))
            val aliceStates = Channel<UiState<UiProfile>>(Channel.UNLIMITED)
            val bobStates = Channel<UiState<UiProfile>>(Channel.UNLIMITED)
            backgroundScope.launch { aliceCache.toUi().collect { aliceStates.send(it) } }
            backgroundScope.launch { bobCache.toUi().collect { bobStates.send(it) } }
            assertIs<UiState.Loading<UiProfile>>(aliceStates.receive())
            assertIs<UiState.Loading<UiProfile>>(bobStates.receive())

            bobResponse.complete(bob)
            val resolvedBob = bobStates.receiveAsFlow().first { it !is UiState.Loading }
            assertEquals(bob, assertIs<UiState.Success<UiProfile>>(resolvedBob).data)
            aliceResponse.complete(alice)
            val resolvedAlice = aliceStates.receiveAsFlow().first { it !is UiState.Loading }
            assertEquals(alice, assertIs<UiState.Success<UiProfile>>(resolvedAlice).data)

            val cachedBob = bobCache.data.filterIsInstance<CacheState.Success<UiProfile>>().first()
            assertEquals(bob, cachedBob.data)
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
    fun resolvedUsersUseTranslatedDescriptionWhenPreTranslationEnabled() =
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

            loader.nextByHandleAndHost = profile
            val resolved =
                handler
                    .userByHandleAndHost(UiHandle("EVE", "test.social"))
                    .toUi()
                    .first { it !is UiState.Loading }
            assertEquals("翻译后的简介", assertIs<UiState.Success<UiProfile>>(resolved).data.description?.raw)
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
    fun userByIdRefreshSkipsProfilePreTranslationForExcludedLanguage() =
        runTest {
            val excludedLanguage = nonTargetLanguageTag()
            val expected =
                createProfile(id = "excluded-pretranslate", host = "test.social", handle = "@excluded-pretranslate@test.social").copy(
                    description = "Original profile bio".toUiPlainText(),
                    sourceLanguages = persistentListOf(excludedLanguage),
                )
            loader.nextById = expected
            appDataStore.appSettingsStore.updateData {
                it.copy(
                    language = "zh-CN",
                    translateConfig =
                        aiPreTranslateConfig().copy(
                            autoTranslateExcludedLanguages = listOf(excludedLanguage),
                        ),
                    aiConfig =
                        AppSettings.AiConfig(
                            type = AppSettings.AiConfig.Type.OnDevice,
                        ),
                )
            }

            val cacheable = handler.userById("excluded-pretranslate")
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
                    .first { it.status == TranslationStatus.Skipped }

            assertEquals(TranslationStatus.Skipped, saved.status)
            assertEquals(PreTranslationStoreSupport.SKIPPED_EXCLUDED_LANGUAGE_REASON, saved.statusReason)
            assertNull(saved.payload)
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
            platformId = "Mastodon",
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(fansCount = 0, followsCount = 0, statusesCount = 0, platformFansCount = "0"),
            mark = persistentListOf(),
            bottomContent = null,
        )

    private class FakeUserLoader : UserLoader {
        var nextByHandleAndHost: UiProfile? = null
        var loadByHandle: suspend (UiHandle) -> UiProfile = { requireNotNull(nextByHandleAndHost) }
        var nextById: UiProfile? = null
        var failById: Boolean = false
        var byHandleCallCount: Int = 0
        var byIdCallCount: Int = 0

        override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile {
            byHandleCallCount++
            return loadByHandle(uiHandle)
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

private fun nonTargetLanguageTag(): String {
    val target = requireNotNull(canonicalTranslationLanguage(Locale.language))
    return listOf("fr-FR", "de-DE", "es-ES", "ja-JP", "zh-CN")
        .first { candidate ->
            canonicalTranslationLanguage(candidate) != target
        }
}
