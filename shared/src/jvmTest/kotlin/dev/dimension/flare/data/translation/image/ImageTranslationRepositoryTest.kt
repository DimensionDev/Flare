package dev.dimension.flare.data.translation.image

import androidx.compose.runtime.snapshots.Snapshot
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import dev.dimension.flare.common.isImageTranslationFile
import dev.dimension.flare.common.prepareImageTranslationExport
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.ImageTranslationConfig
import dev.dimension.flare.data.datastore.model.ImageTranslationProvider
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.network.ai.OpenAIService
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.di.testSingle
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.presenter.media.ImageTranslationPresenter
import dev.dimension.flare.ui.presenter.settings.ImageTranslationSettingsPresenter
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageTranslationRepositoryTest {
    private val fs = FileSystem.SYSTEM
    private val config =
        ImageTranslationConfig(
            ImageTranslationProvider.OpenAIImages,
        ).withValue("apiKey", "key").withValue("model", "image")

    private fun png(
        width: Int,
        height: Int,
    ): ByteString {
        val stream = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", stream)
        return stream.toByteArray().toByteString()
    }

    private class Fixture(
        val root: Path,
        val fs: FileSystem = FileSystem.SYSTEM,
    ) : AutoCloseable {
        val storage = OkioFileStorage(fs, root, cacheRoot = root)
        var original =
            ByteArrayOutputStream()
                .also {
                    ImageIO.write(BufferedImage(2, 4, BufferedImage.TYPE_INT_RGB), "png", it)
                }.toByteArray()
                .toByteString()
        private val count =
            java.util.concurrent.atomic
                .AtomicInteger()
        val calls: Int get() = count.get()
        val configs = ConcurrentLinkedQueue<ImageTranslationConfig>()
        val modelDiscovery = ImageTranslationModelDiscovery(OpenAIService { HttpClient(MockEngine { respond("""{"data":[]}""") }) })
        val output =
            ByteArrayOutputStream()
                .also {
                    ImageIO.write(BufferedImage(7, 1, BufferedImage.TYPE_INT_RGB), "png", it)
                }.toByteArray()
                .toByteString()
        var behavior: suspend (ImageTranslationInput) -> ImageTranslationOutput = { ImageTranslationOutput.Encoded(output.base64()) }
        val client = HttpClient(MockEngine { error("Unexpected live provider request") })
        val adapter =
            object : ImageTranslationAdapter {
                override val provider = ImageTranslationProvider.OpenAIImages

                override suspend fun translate(
                    input: ImageTranslationInput,
                    config: ImageTranslationConfig,
                    target: String,
                ): ImageTranslationOutput {
                    count.incrementAndGet()
                    configs.add(config)
                    assertEquals(original, input.readBytes(1024 * 1024))
                    return behavior(input)
                }
            }

        fun repository(cache: ImageTranslationDiskCache = ImageTranslationDiskCache(fs, root / "cache")) =
            ImageTranslationRepository(
                storage,
                client,
                listOf(adapter),
                cache,
                sourceClient = { HttpClient(MockEngine { respond(original.toByteArray()) }) },
                imageCompressor =
                    dev.dimension.flare.shared.image
                        .JvmImageCompressor(),
            )

        val media = UiMedia.Image("https://source.example/original.png")

        override fun close() {
            client.close()
            fs.deleteRecursively(root)
        }
    }

    private fun fixture(fs: FileSystem = FileSystem.SYSTEM) =
        Fixture(Files.createTempDirectory("image-translation").toString().toPath(), fs)

    @Test
    fun cachesCompleteImageAcceptsDifferentAspectRatioAndInvalidatesChangedSourceBytes() =
        runTest {
            fixture().use { f ->
                val repository = f.repository()
                val session = repository.newSession()
                val first = repository.translate(f.media, null, config, "en-US", session, false)
                assertEquals(7f, first.width)
                assertEquals(1f, first.height)
                assertTrue(File(URI(first.url)).isFile)
                assertEquals(null, first.customHeaders)
                repository.translate(f.media.copy(url = "https://source.example/alias.png"), null, config, "en-US", session, false)
                assertEquals(1, f.calls)
                f.original = png(4, 4)
                repository.translate(f.media, null, config, "en-US", session, false)
                assertEquals(2, f.calls)
                repository.closeSession(session)
                assertFalse(File(URI(first.url)).exists())
            }
        }

    @Test
    fun clearingDuringSubmittedTaskKeepsDisplayButPreventsLateCacheWrite() =
        runTest {
            fixture().use { f ->
                val started = CompletableDeferred<Unit>()
                val finish = CompletableDeferred<Unit>()
                f.behavior = {
                    started.complete(Unit)
                    finish.await()
                    ImageTranslationOutput.Encoded(f.output.base64())
                }
                val repository = f.repository()
                val session = repository.newSession()
                val result = async { repository.translate(f.media, null, config, "en", session, false) }
                started.await()
                repository.clear()
                finish.complete(Unit)
                val media = result.await()
                assertTrue(File(URI(media.url)).exists())
                assertEquals(0L, repository.size())
                repository.closeSession(session)
            }
        }

    @Test
    fun closingViewerCancelsSubmittedClientWait() =
        runTest {
            fixture().use { f ->
                val started = CompletableDeferred<Unit>()
                val cancelled = CompletableDeferred<Unit>()
                f.behavior = {
                    started.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled.complete(Unit)
                    }
                }
                val repository = f.repository()
                val session = repository.newSession()
                val result = async { repository.translate(f.media, null, config, "en", session, false) }
                started.await()
                repository.closeSession(session)
                cancelled.await()
                assertTrue(result.isCancelled)
                assertEquals(0L, repository.size())
            }
        }

    @Test
    fun failedDiskCacheStillProducesSavableImageAndExportSurvivesViewerClose() =
        runTest {
            fixture().use { f ->
                val impossibleCache = f.root / "not-a-directory"
                fs.write(impossibleCache) { writeUtf8("file") }
                val repository = f.repository(ImageTranslationDiskCache(fs, impossibleCache))
                val session = repository.newSession()
                val result = repository.translate(f.media, null, config, "en", session, false)
                val prepared = prepareImageTranslationExport(result, f.root.toFile())
                repository.closeSession(session)
                assertFalse(File(URI(result.url)).exists())
                assertEquals(f.output, fs.read(File(URI(prepared.url)).toString().toPath()) { readByteString() })
                val unowned = UiMedia.Image("file://" + impossibleCache)
                assertFalse(isImageTranslationFile(unowned, f.root.toFile()))
                assertEquals(unowned, prepareImageTranslationExport(unowned, f.root.toFile()))
                assertEquals(result, prepareImageTranslationExport(result, f.root.toFile()))
            }
        }

    @Test
    fun failedRegenerationFinalizationPreservesSuccessfulCache() =
        runTest {
            var failFinalMove = false
            val fs =
                object : ForwardingFileSystem(FileSystem.SYSTEM) {
                    override fun atomicMove(
                        source: Path,
                        target: Path,
                    ) {
                        if (failFinalMove && target.name.contains("_translated_")) throw IOException("Cannot finalize translated image")
                        super.atomicMove(source, target)
                    }
                }
            fixture(fs).use { f ->
                val repository = f.repository()
                val session = repository.newSession()
                f.behavior = { ImageTranslationOutput.Encoded(png(2, 4).base64()) }
                val first = repository.translate(f.media, null, config, "en", session, false)
                f.behavior = { ImageTranslationOutput.Encoded(f.output.base64()) }
                failFinalMove = true
                assertFailsWith<IOException> { repository.translate(f.media, null, config, "en", session, true) }
                failFinalMove = false
                val cached = repository.translate(f.media, null, config, "en", session, false)
                assertEquals(2, f.calls)
                assertEquals(first.width, cached.width)
                assertEquals(first.height, cached.height)
                val regenerated = repository.translate(f.media, null, config, "en", session, true)
                val updatedCache = repository.translate(f.media, null, config, "en", session, false)
                assertEquals(3, f.calls)
                assertEquals(7f, regenerated.width)
                assertEquals(regenerated.width, updatedCache.width)
                assertEquals(regenerated.height, updatedCache.height)
                repository.closeSession(session)
            }
        }

    @Test
    fun ownerIsolationAndInvalidImageNeverBecomeSharedSuccess() =
        runTest {
            fixture().use { f ->
                val repository = f.repository()
                val session = repository.newSession()
                val a = MicroBlogKey("a", "example")
                val b = MicroBlogKey("b", "example")
                repository.translate(f.media, a, config, "en", session, false)
                repository.translate(f.media, b, config, "en", session, false)
                assertEquals(2, f.calls)
                repository.removeOwner(a)
                repository.translate(f.media, b, config, "en", session, false)
                assertEquals(2, f.calls)
                f.behavior = { ImageTranslationOutput.Encoded("not an image".encodeToByteArray().toByteString().base64()) }
                assertEquals(
                    ImageTranslationError.InvalidImage,
                    assertFailsWith<ImageTranslationException> {
                        repository.translate(f.media, a, config, "en", session, false)
                    }.reason,
                )
                repository.closeSession(session)
            }
        }

    @Test
    fun maintenanceRemovesAbandonedSessionsAndKeepsActiveUnicodeResultSavable() =
        runTest {
            fixture().use { f ->
                val stale = f.root / "image_translation_sessions" / "abandoned"
                fs.createDirectories(stale)
                fs.write(stale / "result.png") { write(f.output) }
                val repository = f.repository()
                repository.clear()
                assertFalse(fs.exists(stale))
                val session = repository.newSession()
                val media = f.media.copy(url = "https://source.example/" + "像".repeat(100) + ".png")
                val result = repository.translate(media, null, config, "en", session, false)
                repository.clear()
                val file = File(URI(result.url))
                assertTrue(file.exists())
                assertTrue(file.name.encodeToByteArray().size <= 255)
                repository.closeSession(session)
                assertFalse(file.exists())
            }
        }

    @Test
    fun unidentifiedCustomCredentialsDoNotPersistPrivateResults() =
        runTest {
            fixture().use { f ->
                val repository = f.repository()
                val session = repository.newSession()
                val privateMedia =
                    f.media.copy(
                        customHeaders = kotlinx.collections.immutable.persistentMapOf("X-Media-Token" to "private-token"),
                    )
                repository.translate(privateMedia, null, config, "en", session, false)
                repository.translate(privateMedia, null, config, "en", session, false)
                assertEquals(2, f.calls)
                assertEquals(0L, repository.size())
                repository.closeSession(session)
            }
        }

    @Test
    fun animatedPngIsRejectedBeforeProviderSubmission() =
        runTest {
            fixture().use { f ->
                val bytes = f.original.toByteArray()
                f.original =
                    (
                        bytes.copyOfRange(0, 33) +
                            okio
                                .Buffer()
                                .apply {
                                    writeInt(8)
                                    writeUtf8("acTL")
                                    writeInt(2)
                                    writeInt(0)
                                    writeInt(0)
                                }.readByteArray() + bytes.copyOfRange(33, bytes.size)
                    ).toByteString()
                val repository = f.repository()
                val session = repository.newSession()
                assertEquals(
                    ImageTranslationError.UnsupportedInput,
                    assertFailsWith<ImageTranslationException> {
                        repository.translate(f.media, null, config, "en", session, false)
                    }.reason,
                )
                assertEquals(0, f.calls)
                repository.closeSession(session)
            }
        }

    @Test
    fun presenterDeduplicatesAndKeepsResultsAcrossPagingAndInvalidConfiguration() =
        runBlocking {
            withTimeout(10_000) {
                fixture().use { f ->
                    val repository = f.repository()
                    val store = AppDataStore(f.storage)
                    store.appSettingsStore.updateData { it.copy(imageTranslationConfig = config) }
                    startKoin {
                        modules(
                            module {
                                testSingle { store }
                                testSingle { f.modelDiscovery }
                                testSingle { repository }
                                testSingle<CoroutineScope> { CoroutineScope(Dispatchers.Default + SupervisorJob()) }
                            },
                        )
                    }
                    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                    // Drive snapshot notifications that a real UI frame loop normally sends.
                    scope.launch {
                        while (isActive) {
                            Snapshot.sendApplyNotifications()
                            delay(5)
                        }
                    }
                    val a = f.media
                    val b = a.copy(url = "https://source.example/second.png")
                    val aStarted = CompletableDeferred<Unit>()
                    val bStarted = CompletableDeferred<Unit>()
                    val aFinish = CompletableDeferred<Unit>()
                    val bFinish = CompletableDeferred<Unit>()
                    f.behavior = { input ->
                        if (input.publicUrl == a.url) {
                            aStarted.complete(Unit)
                            aFinish.await()
                        } else {
                            bStarted.complete(Unit)
                            bFinish.await()
                        }
                        ImageTranslationOutput.Encoded(f.output.base64())
                    }
                    val models = scope.launchMolecule(RecompositionMode.Immediate) { ImageTranslationPresenter().body() }
                    try {
                        var state = models.first { it.configured }
                        state.toggle(a)
                        state.toggle(a)
                        state.toggle(b)
                        aStarted.await()
                        bStarted.await()
                        assertEquals(2, f.calls)
                        bFinish.complete(Unit)
                        state = models.first { it.item(b).translated != null || it.item(b).error != null }
                        assertEquals(null, state.item(b).error)
                        val bResult = state.item(b).translated!!
                        assertTrue(state.item(a).loading)
                        assertEquals(a, state.displayed(a))
                        assertEquals(bResult, state.displayed(b))
                        store.appSettingsStore.updateData {
                            it.copy(imageTranslationConfig = config.copy(provider = ImageTranslationProvider.Disabled))
                        }
                        state = models.first { !it.configured }
                        assertFalse(state.canToggle(a))
                        assertTrue(state.canToggle(b))
                        assertFalse(state.canRegenerate(b))
                        assertTrue(state.item(a).loading)
                        assertEquals(bResult, state.displayed(b))
                        state.toggle(b)
                        state = models.first { !it.item(b).showingTranslated }
                        assertEquals(b, state.displayed(b))
                        state.toggle(b)
                        state = models.first { it.item(b).showingTranslated }
                        assertEquals(bResult, state.displayed(b))
                        val untouched = a.copy(url = "https://source.example/untouched.png")
                        state.toggle(untouched)
                        state.regenerate(b)
                        aFinish.complete(Unit)
                        state = models.first { it.item(a).translated != null }
                        assertFalse(state.configured)
                        assertEquals(2, f.calls)
                        assertEquals(null, state.item(untouched).error)
                        assertFalse(state.item(untouched).loading)
                        assertEquals(null, state.item(b).error)
                        assertEquals(bResult, state.displayed(b))
                        val cachedSession = repository.newSession()
                        try {
                            repository.translate(a, null, config, dev.dimension.flare.common.Locale.language, cachedSession, false)
                            assertEquals(2, f.calls)
                        } finally {
                            repository.closeSession(cachedSession)
                        }
                        store.appSettingsStore.updateData { it.copy(imageTranslationConfig = config) }
                        state = models.first { it.configured }
                        assertEquals(bResult, state.displayed(b))
                        f.behavior = {
                            throw ImageTranslationException(
                                ImageTranslationError.Rejected,
                                diagnostics =
                                    ImageTranslationDiagnostics(
                                        400,
                                        "invalid_request",
                                        "Image request was rejected: key",
                                        "failure-request",
                                    ),
                            )
                        }
                        state.regenerate(b)
                        state = models.first { it.item(b).error == ImageTranslationError.Rejected }
                        val details = requireNotNull(state.item(b).errorDetails)
                        assertContains(details, "Stage: Translate image")
                        assertContains(details, "Provider code: invalid_request")
                        assertContains(details, "Request ID: failure-request")
                        assertContains(details, "Image request was rejected: key")
                        assertEquals(null, state.item(a).errorDetails)
                        state.clearError(b)
                        state = models.first { it.item(b).error == null }
                        assertEquals(details, state.item(b).errorDetails)
                        state.setErrorDetailsVisible(b, true)
                        state = models.first { it.item(b).errorDetailsVisible }
                        assertEquals(details, state.item(b).errorDetails)
                        assertEquals(3, f.calls)
                        state.setErrorDetailsVisible(b, false)
                        state = models.first { !it.item(b).errorDetailsVisible }
                        state.toggle(b)
                        state = models.first { !it.item(b).showingTranslated }
                        assertEquals(details, state.item(b).errorDetails)
                        state.toggle(b)
                        state = models.first { it.item(b).showingTranslated }
                        assertTrue(state.canToggle(b))
                        assertTrue(state.canRegenerate(b))
                        assertEquals(bResult, state.displayed(b))
                        f.behavior = { ImageTranslationOutput.Encoded(f.output.base64()) }
                        state.regenerate(b)
                        state = models.first { it.item(b).translated != null && it.item(b).translated?.url != bResult.url }
                        val latest = state.item(b).translated!!
                        assertEquals(null, state.item(b).errorDetails)
                        state.imageFailed(b, "file:///stale-result.png")
                        assertEquals(latest, models.value.displayed(b))
                        state.imageFailed(b, latest.url)
                        state = models.first { it.item(b).error == ImageTranslationError.InvalidImage }
                        assertEquals(bResult, state.displayed(b))
                    } finally {
                        withContext(NonCancellable) {
                            scope.coroutineContext[Job]!!.cancelAndJoin()
                            stopKoin()
                        }
                    }
                }
            }
        }

    @Test
    fun displayFailureKeepsRegenerationInFlightAndRetainsFallback() =
        runBlocking {
            withTimeout(10_000) {
                fixture().use { f ->
                    val repository = f.repository()
                    val store = AppDataStore(f.storage)
                    store.appSettingsStore.updateData { it.copy(imageTranslationConfig = config) }
                    startKoin {
                        modules(
                            module {
                                testSingle { store }
                                testSingle { f.modelDiscovery }
                                testSingle { repository }
                                testSingle<CoroutineScope> { CoroutineScope(Dispatchers.Default + SupervisorJob()) }
                            },
                        )
                    }
                    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                    scope.launch {
                        while (isActive) {
                            Snapshot.sendApplyNotifications()
                            delay(5)
                        }
                    }
                    val started = CompletableDeferred<Unit>()
                    val finish = CompletableDeferred<Unit>()
                    val models = scope.launchMolecule(RecompositionMode.Immediate) { ImageTranslationPresenter().body() }
                    try {
                        var state = models.first { it.configured }
                        state.toggle(f.media)
                        state = models.first { it.item(f.media).translated != null }
                        val first = state.item(f.media).translated!!
                        state.regenerate(f.media)
                        state = models.first { it.item(f.media).translated?.url != first.url && it.item(f.media).translated != null }
                        val previous = state.item(f.media).translated!!
                        f.behavior = {
                            started.complete(Unit)
                            finish.await()
                            ImageTranslationOutput.Encoded(png(3, 7).base64())
                        }
                        state.regenerate(f.media)
                        started.await()
                        state = models.first { it.item(f.media).loading }
                        state.imageFailed(f.media, previous.url)
                        state = models.first { it.item(f.media).error == ImageTranslationError.InvalidImage }
                        assertTrue(state.item(f.media).loading)
                        assertEquals(first, state.displayed(f.media))
                        state.clearError(f.media)
                        state.regenerate(f.media)
                        state.toggle(f.media)
                        finish.complete(Unit)
                        state = models.first { !it.item(f.media).loading && it.item(f.media).error == null }
                        assertEquals(3, f.calls)
                        val translated = state.item(f.media).translated!!
                        assertEquals(3f, translated.width)
                        assertEquals(7f, translated.height)
                        assertEquals(translated, state.displayed(f.media))
                    } finally {
                        finish.complete(Unit)
                        withContext(NonCancellable) {
                            scope.coroutineContext[Job]!!.cancelAndJoin()
                            stopKoin()
                        }
                    }
                }
            }
        }

    @Test
    fun settingsPersistLastEditEvenWhenEditorClosesImmediately() =
        runBlocking {
            withTimeout(10_000) {
                fixture().use { f ->
                    val store = AppDataStore(f.storage)
                    store.appSettingsStore.updateData { it.copy(imageTranslationConfig = config) }
                    startKoin {
                        modules(
                            module {
                                testSingle { store }
                                testSingle { f.modelDiscovery }
                                testSingle<CoroutineScope> { CoroutineScope(Dispatchers.Default + SupervisorJob()) }
                            },
                        )
                    }
                    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                    // Drive snapshot notifications that a real UI frame loop normally sends.
                    scope.launch {
                        while (isActive) {
                            Snapshot.sendApplyNotifications()
                            delay(5)
                        }
                    }
                    val models = scope.launchMolecule(RecompositionMode.Immediate) { ImageTranslationSettingsPresenter().body() }
                    try {
                        val state = models.first { it.config.provider == ImageTranslationProvider.OpenAIImages }
                        state.setField("apiKey", "first")
                        state.setField("apiKey", "second")
                        state.setField("apiKey", "last-complete-key")
                        scope.coroutineContext[Job]!!.cancelAndJoin()
                        val saved = store.appSettingsStore.data.first { it.imageTranslationConfig.value("apiKey") == "last-complete-key" }
                        assertEquals(config.value("model"), saved.imageTranslationConfig.value("model"))
                    } finally {
                        withContext(NonCancellable) {
                            scope.coroutineContext[Job]!!.cancelAndJoin()
                            stopKoin()
                        }
                    }
                }
            }
        }

    @Test
    fun settingsInheritBasicConfigurationAndPersistCustomModeAcrossProviderSwitches() =
        runBlocking {
            withTimeout(10_000) {
                fixture().use { f ->
                    val store = AppDataStore(f.storage)
                    val basic = AppSettings.AiConfig.Type.OpenAI("https://basic.example/v1", "basic-key", "basic-model")
                    store.appSettingsStore.updateData { it.copy(aiConfig = it.aiConfig.copy(type = basic)) }
                    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                    startKoin {
                        modules(
                            module {
                                testSingle { store }
                                testSingle { f.modelDiscovery }
                                testSingle<CoroutineScope> { scope }
                            },
                        )
                    }
                    scope.launch {
                        while (isActive) {
                            Snapshot.sendApplyNotifications()
                            delay(5)
                        }
                    }
                    val models = scope.launchMolecule(RecompositionMode.Immediate) { ImageTranslationSettingsPresenter().body() }
                    try {
                        models.first().selectProvider(ImageTranslationProvider.OpenAIImages)
                        var state = models.first { it.useSharedConfig && it.config.value("model") == "basic-model" }
                        assertEquals(listOf("baseUrl", "apiKey", "model", "inputFormat", "prompt"), state.fields.map { it.id })
                        assertEquals(listOf("inputFormat", "prompt"), state.config.editableFields.map { it.id })
                        assertTrue(
                            store.appSettingsStore.data
                                .first()
                                .imageTranslationConfig.settings
                                .isEmpty(),
                        )
                        state.setUseSharedConfig(false)
                        state = models.first { !it.useSharedConfig && it.config.value("apiKey") == "basic-key" }
                        state.setField("model", "custom-image-model")
                        models.first { it.config.value("model") == "custom-image-model" }
                        store.appSettingsStore.updateData {
                            it.copy(
                                aiConfig = it.aiConfig.copy(type = basic.copy(model = "new-basic-model")),
                            )
                        }
                        state.setUseSharedConfig(true)
                        state = models.first { it.useSharedConfig && it.config.value("model") == "new-basic-model" }
                        state.selectProvider(ImageTranslationProvider.Gemini)
                        models.first { it.config.provider == ImageTranslationProvider.Gemini }.selectProvider(
                            ImageTranslationProvider.OpenAIImages,
                        )
                        state = models.first { it.useSharedConfig }
                        state.setUseSharedConfig(false)
                        models.first { !it.useSharedConfig && it.config.value("model") == "new-basic-model" }
                        val saved = store.appSettingsStore.data.first()
                        assertFalse(saved.imageTranslationConfig.useSharedAIConfig)
                        assertEquals("new-basic-model", saved.imageTranslationConfig.value("model"))
                        assertEquals("basic-key", saved.imageTranslationConfig.value("apiKey"))
                    } finally {
                        withContext(NonCancellable) {
                            scope.coroutineContext[Job]!!.cancelAndJoin()
                            stopKoin()
                        }
                    }
                }
            }
        }

    @Test
    fun basicConfigurationChangeScopesSubmittedResultsToTheirOriginalModel() =
        runBlocking {
            withTimeout(10_000) {
                fixture().use { f ->
                    val repository = f.repository()
                    val store = AppDataStore(f.storage)
                    val basic = AppSettings.AiConfig.Type.OpenAI("https://basic.example/v1", "basic-key", "first-image-model")
                    store.appSettingsStore.updateData {
                        it.copy(
                            aiConfig = it.aiConfig.copy(type = basic),
                            imageTranslationConfig =
                                ImageTranslationConfig(
                                    ImageTranslationProvider.OpenAIImages,
                                    useSharedAIConfig = true,
                                ),
                        )
                    }
                    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                    startKoin {
                        modules(
                            module {
                                testSingle { store }
                                testSingle { f.modelDiscovery }
                                testSingle { repository }
                                testSingle<CoroutineScope> { scope }
                            },
                        )
                    }
                    scope.launch {
                        while (isActive) {
                            Snapshot.sendApplyNotifications()
                            delay(5)
                        }
                    }
                    val firstStarted = CompletableDeferred<Unit>()
                    val finishFirst = CompletableDeferred<Unit>()
                    f.behavior = {
                        if (f.calls == 1) {
                            firstStarted.complete(Unit)
                            finishFirst.await()
                        }
                        ImageTranslationOutput.Encoded(f.output.base64())
                    }
                    val models =
                        scope.launchMolecule(RecompositionMode.Immediate) {
                            ImageTranslationPresenter().body() to ImageTranslationSettingsPresenter().body()
                        }
                    try {
                        var state = models.first { it.second.config.value("model") == "first-image-model" }.first
                        state.toggle(f.media)
                        firstStarted.await()
                        store.appSettingsStore.updateData {
                            it.copy(
                                aiConfig = it.aiConfig.copy(type = basic.copy(model = "second-image-model")),
                            )
                        }
                        state =
                            models
                                .first {
                                    it.second.config.value("model") == "second-image-model" && !it.first.item(f.media).loading
                                }.first
                        assertFalse(state.item(f.media).loading)
                        state.toggle(f.media)
                        state = models.first { it.first.item(f.media).translated != null }.first
                        val secondResult = state.displayed(f.media)
                        finishFirst.complete(Unit)
                        store.appSettingsStore.updateData { it.copy(aiConfig = it.aiConfig.copy(type = basic)) }
                        state =
                            models
                                .first {
                                    it.second.config.value(
                                        "model",
                                    ) == "first-image-model" && it.first.item(f.media).translated != null &&
                                        it.first.displayed(f.media) != secondResult
                                }.first
                        assertTrue(state.displayed(f.media) != secondResult)
                        store.appSettingsStore.updateData {
                            it.copy(
                                aiConfig = it.aiConfig.copy(type = basic.copy(model = "second-image-model")),
                            )
                        }
                        state =
                            models
                                .first {
                                    it.second.config.value("model") == "second-image-model" && it.first.displayed(f.media) == secondResult
                                }.first
                        assertEquals(secondResult, state.displayed(f.media))
                        assertEquals(2, f.calls)
                        assertEquals(listOf("first-image-model", "second-image-model"), f.configs.map { it.value("model") })
                        assertTrue(f.configs.all { it.value("baseUrl") == basic.serverUrl && it.value("apiKey") == basic.apiKey })
                    } finally {
                        withContext(NonCancellable) {
                            scope.coroutineContext[Job]!!.cancelAndJoin()
                            stopKoin()
                        }
                    }
                }
            }
        }

    @Test
    fun modelDiscoveryTracksOnlyConnectionAndNeverOverwritesManualSelection() =
        runBlocking {
            withTimeout(10_000) {
                fixture().use { f ->
                    val store = AppDataStore(f.storage)
                    store.appSettingsStore.updateData {
                        it.copy(
                            imageTranslationConfig = config.withValue("baseUrl", "https://old.example/v1"),
                        )
                    }
                    val started = CompletableDeferred<Unit>()
                    val finishOld = CompletableDeferred<Unit>()
                    val calls = ConcurrentLinkedQueue<String>()
                    val discovery =
                        ImageTranslationModelDiscovery(
                            OpenAIService {
                                HttpClient(
                                    MockEngine { request ->
                                        calls.add(request.url.host)
                                        if (request.url.host == "old.example") {
                                            started.complete(Unit)
                                            withContext(NonCancellable) { finishOld.await() }
                                            respond("""{"data":[{"id":"stale-image"}]}""")
                                        } else if (request.headers["Authorization"] == "Bearer bad-key") {
                                            respond("{}", HttpStatusCode.Unauthorized)
                                        } else {
                                            respond("""{"data":[{"id":"detected-image"}]}""")
                                        }
                                    },
                                )
                            },
                        )
                    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                    startKoin {
                        modules(
                            module {
                                testSingle { store }
                                testSingle { discovery }
                                testSingle<CoroutineScope> { scope }
                            },
                        )
                    }
                    scope.launch {
                        while (isActive) {
                            Snapshot.sendApplyNotifications()
                            delay(5)
                        }
                    }
                    val models = scope.launchMolecule(RecompositionMode.Immediate) { ImageTranslationSettingsPresenter().body() }
                    try {
                        started.await()
                        models.value.setField("baseUrl", "https://new.example/v1")
                        var state = models.first { it.config.value("baseUrl") == "https://new.example/v1" }
                        assertTrue(state.fields.single { it.id == "model" }.loading)
                        assertEquals(emptyList(), state.fields.single { it.id == "model" }.suggestions)
                        state = models.first { it.fields.single { it.id == "model" }.suggestions == listOf("detected-image") }
                        finishOld.complete(Unit)
                        state.setField("model", "manual-image")
                        state = models.first { it.config.value("model") == "manual-image" }
                        state.setField("prompt", "Changed image prompt")
                        models.first { it.config.value("prompt") == "Changed image prompt" }
                        delay(750)
                        assertEquals(listOf("old.example", "new.example"), calls.toList())
                        assertEquals(
                            listOf("detected-image"),
                            models.value.fields
                                .single { it.id == "model" }
                                .suggestions,
                        )
                        models.value.setField("apiKey", "bad-key")
                        models.first { it.config.value("apiKey") == "bad-key" && it.fields.single { it.id == "model" }.loading }
                        state = models.first { it.config.value("apiKey") == "bad-key" && !it.fields.single { it.id == "model" }.loading }
                        assertEquals(emptyList(), state.fields.single { it.id == "model" }.suggestions)
                        assertEquals("manual-image", state.config.value("model"))
                        assertEquals(3, calls.size)
                    } finally {
                        finishOld.complete(Unit)
                        withContext(NonCancellable) {
                            scope.coroutineContext[Job]!!.cancelAndJoin()
                            stopKoin()
                        }
                    }
                }
            }
        }
}
