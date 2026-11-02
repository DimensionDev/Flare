package dev.dimension.flare.data.translation

import androidx.room3.Room
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.Locale
import dev.dimension.flare.common.OnDeviceAI
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbTranslation
import dev.dimension.flare.data.database.cache.model.TranslationDisplayMode
import dev.dimension.flare.data.database.cache.model.TranslationEntityType
import dev.dimension.flare.data.database.cache.model.TranslationPayload
import dev.dimension.flare.data.database.cache.model.TranslationStatus
import dev.dimension.flare.data.database.cache.model.sourceHash
import dev.dimension.flare.data.database.cache.model.statusTranslationEntityKey
import dev.dimension.flare.data.database.cache.model.translationPayload
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.paging.TimelineDbPageCache
import dev.dimension.flare.data.datasource.microblog.paging.TimelineDbPageLoader
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.network.ai.AiCompletionService
import dev.dimension.flare.data.network.ai.OpenAIService
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.ui.model.TranslationDisplayState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.asTimelinePostItem
import dev.dimension.flare.ui.model.createSampleStatus
import dev.dimension.flare.ui.model.createSampleUser
import dev.dimension.flare.ui.render.TranslationTokenKind
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlatformTranslationServiceTest : RobolectricTest() {
    private val root = createTestRootPath()
    private val appDataStore = AppDataStore(OkioFileStorage(createTestFileSystem(), root))
    private lateinit var db: CacheDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var service: PreTranslationService
    private val requests = mutableListOf<String>()
    private var translate: suspend (PreTranslationBatchDocument) -> String? = ::translatedJson
    private val pageLoader by lazy { TimelineDbPageLoader(db, "translation-test", TimelineDbPageCache()) }

    @BeforeTest
    fun setup() =
        runTest {
            db =
                Room
                    .memoryDatabaseBuilder<CacheDatabase>()
                    .setDriver(createDatabaseDriver())
                    .setQueryCoroutineContext(Dispatchers.Unconfined)
                    .build()
            appDataStore.appSettingsStore.updateData {
                it.copy(language = Locale.language, translateConfig = aiPreTranslateConfig().copy(preferPlatformTranslation = true))
            }
        }

    @AfterTest
    fun tearDown() {
        if (::scope.isInitialized) scope.cancel()
        db.close()
        deleteTestRootPath(root)
    }

    @Test
    fun queuedStatusSnapshotsCannotRecreateDeletedTranslations() =
        runTest {
            startService()
            val status = save(post(platformTranslation = "deleted private translation"))
            db.statusDao().deleteByAccountType(status.accountType)
            service.enqueueStatuses(listOf(status))
            runCurrent()
            assertNull(db.translationDao().get(TranslationEntityType.Status, status.id, Locale.language))
            assertTrue(requests.isEmpty())
        }

    @Test
    fun platformUpdatesKeepTimelineAndDetailTranslated() =
        runTest {
            startService()
            val first = post(platformTranslation = "platform A")
            service.enqueueStatuses(listOf(save(first)))
            val initial = awaitTranslation(first) { it.payload?.content?.raw == "platform A" }
            assertDisplayed(first, "platform A")

            val missing = first.copy(content = first.content.copy(translation = null))
            service.enqueueStatuses(listOf(save(missing)))
            runCurrent()
            assertDisplayed(missing, "platform A")

            val updated = first.copy(content = first.content.copy(translation = "platform B".toUiPlainText()))
            val updatedStatus = save(updated)
            assertDisplayed(updated, "platform A")
            service.enqueueStatuses(listOf(updatedStatus))
            val completed = awaitTranslation(updated) { it.payload?.content?.raw == "platform B" }
            assertEquals(initial.sourceHash, completed.sourceHash)
            assertDisplayed(updated, "platform B")

            service.enqueueStatuses(listOf(updatedStatus))
            runCurrent()
            assertEquals(completed, currentTranslation(updated))
            assertEquals(emptyList(), requests)
        }

    @Test
    fun legacyPlatformCacheMigratesWithoutProviderCallsAndKeepsOriginalMode() =
        runTest {
            startService()
            val post = post()
            val status = save(post)
            val source = requireNotNull(post.translationPayload())
            val payload = TranslationPayload(content = "legacy platform".toUiPlainText())
            val legacy =
                DbTranslation(
                    entityType = TranslationEntityType.Status,
                    entityKey = status.id,
                    targetLanguage = Locale.language,
                    sourceHash = source.legacyPlatformSourceHash("xQt", payload),
                    status = TranslationStatus.Completed,
                    displayMode = TranslationDisplayMode.Original,
                    payload = payload,
                    updatedAt = 1,
                )
            db.translationDao().insert(legacy)
            assertDisplayed(post, "legacy platform", TranslationDisplayState.Hidden)
            service.enqueueStatuses(listOf(status))
            val migrated = awaitTranslation(post) { it.sourceHash == source.sourceHash("platform:xQt") }

            assertNotEquals(legacy.sourceHash, migrated.sourceHash)
            assertEquals(payload, migrated.payload)
            assertEquals(TranslationDisplayMode.Original, migrated.displayMode)
            assertDisplayed(post, "legacy platform", TranslationDisplayState.Hidden)
            assertEquals(emptyList(), requests)
        }

    @Test
    fun providerTranslationRemainsVisibleUntilPlatformImportCompletes() =
        runTest {
            startService()
            val original = post()
            service.enqueueStatuses(listOf(save(original)))
            awaitTranslation(original) { it.status == TranslationStatus.Completed }
            assertDisplayed(original, "source sentence (translated)")

            val platform = original.copy(content = original.content.copy(translation = "platform".toUiPlainText()))
            val status = save(platform)
            assertDisplayed(platform, "source sentence (translated)")
            service.enqueueStatuses(listOf(status))
            awaitTranslation(platform) { it.payload?.content?.raw == "platform" }
            assertDisplayed(platform, "platform")
            assertEquals(listOf(status.id), requests)
        }

    @Test
    fun lateProviderSuccessCannotReplaceCompletedPlatformTranslation() = checkLateProvider(fail = false)

    @Test
    fun lateProviderFailureCannotReplaceCompletedPlatformTranslation() = checkLateProvider(fail = true)

    private fun checkLateProvider(fail: Boolean) =
        runTest {
            val worker = CompletableDeferred<Job>()
            val release = CompletableDeferred<Unit>()
            translate = {
                worker.complete(currentCoroutineContext().job)
                release.await()
                if (fail) error("provider failed")
                translatedJson(it)
            }
            startService()
            val original = post()
            service.enqueueStatuses(listOf(save(original)))
            val job = withTimeout(5_000) { worker.await() }

            val platform = original.copy(content = original.content.copy(translation = "platform".toUiPlainText()))
            service.enqueueStatuses(listOf(save(platform)))
            val imported = awaitTranslation(platform) { it.payload?.content?.raw == "platform" }
            release.complete(Unit)
            withTimeout(5_000) { job.join() }

            assertEquals(imported, currentTranslation(platform))
            assertDisplayed(platform, "platform")
        }

    @Test
    fun queuedProviderWorkDoesNotReplacePlatformTranslationOrCallProvider() =
        runTest {
            val worker = CompletableDeferred<Job>()
            val release = CompletableDeferred<Unit>()
            translate = {
                worker.complete(currentCoroutineContext().job)
                release.await()
                translatedJson(it)
            }
            startService()
            val first = post(key = "first")
            service.enqueueStatuses(listOf(save(first)))
            val job = withTimeout(5_000) { worker.await() }
            val queued = post(key = "queued")
            service.enqueueStatuses(listOf(save(queued)))
            awaitTranslation(queued) { it.status == TranslationStatus.Pending }

            val platform = queued.copy(content = queued.content.copy(translation = "platform".toUiPlainText()))
            service.enqueueStatuses(listOf(save(platform)))
            val imported = awaitTranslation(platform) { it.payload?.content?.raw == "platform" }
            release.complete(Unit)
            withTimeout(5_000) { job.join() }
            runCurrent()

            assertEquals(imported, currentTranslation(platform))
            assertEquals(listOf(statusTranslationEntityKey(first.accountType, first.statusKey)), requests)
        }

    @Test
    fun completedProviderWorkKeepsTheLatestShowOriginalChoice() =
        runTest {
            val worker = CompletableDeferred<Job>()
            val release = CompletableDeferred<Unit>()
            translate = {
                worker.complete(currentCoroutineContext().job)
                release.await()
                translatedJson(it)
            }
            startService()
            val post = post()
            service.enqueueStatuses(listOf(save(post)))
            val job = withTimeout(5_000) { worker.await() }
            service.setStatusDisplayMode(post.accountType, post.statusKey, TranslationDisplayMode.Original)
            awaitTranslation(post) { it.displayMode == TranslationDisplayMode.Original }
            release.complete(Unit)
            withTimeout(5_000) { job.join() }

            assertEquals(TranslationDisplayMode.Original, currentTranslation(post).displayMode)
            assertDisplayed(post, "source sentence (translated)", TranslationDisplayState.Hidden)
        }

    @Test
    fun editedSourceSupersedesTheInFlightProviderAttempt() =
        runTest {
            val worker = CompletableDeferred<Job>()
            val release = CompletableDeferred<Unit>()
            translate = {
                worker.complete(currentCoroutineContext().job)
                release.await()
                translatedJson(it)
            }
            startService()
            val original = post()
            service.enqueueStatuses(listOf(save(original)))
            val job = withTimeout(5_000) { worker.await() }
            val edited = post(original = "edited sentence")
            service.enqueueStatuses(listOf(save(edited)))
            val pending = awaitTranslation(edited) { it.status == TranslationStatus.Pending }
            assertEquals(requireNotNull(edited.translationPayload()).sourceHash("ai"), pending.sourceHash)
            release.complete(Unit)
            withTimeout(5_000) { job.join() }
            awaitTranslation(edited) { it.status == TranslationStatus.Completed }

            assertDisplayed(edited, "edited sentence (translated)")
            assertEquals(pending.sourceHash, currentTranslation(edited).sourceHash)
        }

    private fun TestScope.startService() {
        scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val unusedAI =
            object : OnDeviceAI {
                override suspend fun isAvailable(): Boolean = false

                override suspend fun translate(
                    source: String,
                    targetLanguage: String,
                    prompt: String,
                ): String? = error("unused")

                override suspend fun tldr(
                    source: String,
                    targetLanguage: String,
                    prompt: String,
                ): String? = error("unused")
            }
        service =
            OnlinePreTranslationService(
                database = db,
                appDataStore = appDataStore,
                aiCompletionService = AiCompletionService(OpenAIService(), unusedAI),
                coroutineScope = scope,
                batchTranslator = { _, _, _, document, _, _ ->
                    requests.addAll(document.items.map { it.entityKey })
                    translate(document)
                },
            )
    }

    private fun post(
        key: String = "post",
        platformTranslation: String? = null,
        original: String = "source sentence",
    ): UiTimelineV2.Post {
        val sample = createSampleStatus(createSampleUser())
        return sample.copy(
            platformId = "xQt",
            statusKey = sample.statusKey.copy(id = key),
            content =
                UiTranslatableText(
                    original.toUiPlainText(listOf(if (Locale.language.startsWith("en")) "ja" else "en")),
                    platformTranslation?.toUiPlainText(),
                ),
        )
    }

    private suspend fun save(post: UiTimelineV2.Post): DbStatus {
        val item = TimelinePagingMapper.toDb(post, "translation-test")
        saveToDatabase(db, listOf(item))
        return item.status.status.data
    }

    private suspend fun currentTranslation(post: UiTimelineV2.Post): DbTranslation =
        assertNotNull(
            db.translationDao().get(
                TranslationEntityType.Status,
                statusTranslationEntityKey(post.accountType, post.statusKey),
                Locale.language,
            ),
        )

    private suspend fun awaitTranslation(
        post: UiTimelineV2.Post,
        predicate: (DbTranslation) -> Boolean,
    ): DbTranslation =
        withTimeout(5_000) {
            db
                .translationDao()
                .find(
                    TranslationEntityType.Status,
                    statusTranslationEntityKey(post.accountType, post.statusKey),
                    Locale.language,
                ).filterNotNull()
                .first(predicate)
        }

    private suspend fun assertDisplayed(
        post: UiTimelineV2.Post,
        text: String,
        state: TranslationDisplayState = TranslationDisplayState.Translated,
    ) {
        val options = TranslationSettingsSupport.displayOptions(appDataStore.appSettingsStore.data.first())
        val detailData = assertNotNull(db.statusDao().getWithReferencesSync(post.statusKey, post.accountType as DbAccountType))
        val detail = requireNotNull(TimelinePagingMapper.toUi(detailData, "detail", options).asTimelinePostItem()).displayPost
        val page =
            (
                pageLoader.load(0, 20).map { it.toUi(options) }.filterIsInstance<UiTimelineV2.TimelinePostItem>().single {
                    it.statusKey ==
                        post.statusKey
                }
            ).displayPost
        for (rendered in listOf(detail, page)) {
            assertEquals(state, rendered.translationDisplayState)
            assertEquals(text, rendered.content.translation?.raw)
        }
    }

    private fun translatedJson(document: PreTranslationBatchDocument): String =
        document
            .copy(
                items =
                    document.items.map { item ->
                        item.copy(
                            payload =
                                requireNotNull(item.payload).copy(
                                    content =
                                        item.payload.content?.let { content ->
                                            content.copy(
                                                blocks =
                                                    content.blocks.map { block ->
                                                        block.copy(
                                                            tokens =
                                                                block.tokens.map { token ->
                                                                    if (token.kind ==
                                                                        TranslationTokenKind.Translatable
                                                                    ) {
                                                                        token.copy(text = "${token.text} (translated)")
                                                                    } else {
                                                                        token
                                                                    }
                                                                },
                                                        )
                                                    },
                                            )
                                        },
                                ),
                        )
                    },
            ).encodeJson(PreTranslationBatchDocument.serializer())
}
