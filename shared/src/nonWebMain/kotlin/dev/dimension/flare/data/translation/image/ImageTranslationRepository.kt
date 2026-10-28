package dev.dimension.flare.data.translation.image

import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.datastore.model.ImageTranslationConfig
import dev.dimension.flare.data.io.FileStorage
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.network.applyMedia
import dev.dimension.flare.data.network.mediaHttpClient
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiMedia
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Url
import io.ktor.http.decodeURLPart
import io.ktor.http.encodeURLPath
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

@Single
internal fun imageTranslationRepository(
    fileStorage: FileStorage,
    scope: CoroutineScope,
): ImageTranslationRepository {
    val storage = requireNotNull(fileStorage as? OkioFileStorage)
    val client = imageTranslationHttpClient()
    scope.coroutineContext[Job]?.invokeOnCompletion { client.close() }
    return ImageTranslationRepository(storage, client)
}

internal actual class ImageTranslationRepository(
    private val storage: OkioFileStorage,
    private val client: HttpClient,
    private val adapters: List<ImageTranslationAdapter> =
        listOf(
            TencentImageTranslationAdapter(client),
            OpenAIImageTranslationAdapter(client),
            GeminiImageTranslationAdapter(client),
            QwenImageTranslationAdapter(client),
        ),
    private val cache: ImageTranslationDiskCache =
        ImageTranslationDiskCache(
            storage.fileSystem,
            storage.cacheDirectory() / "image_translation",
        ),
    private val sourceClient: (ProxyRef) -> HttpClient = ::mediaHttpClient,
) {
    private val fs = storage.fileSystem
    private val sessions = storage.cacheDirectory() / "image_translation_sessions"
    private val mutex = Mutex()
    private val active = mutableMapOf<Job, Pair<String, String>>()
    private val closedSessions = mutableSetOf<String>()
    private var cleanedSessions = false
    private val permits = Semaphore(2)

    actual fun newSession(): String = Uuid.random().toString()

    actual suspend fun translate(
        media: UiMedia.Image,
        owner: MicroBlogKey?,
        config: ImageTranslationConfig,
        target: String,
        session: String,
        regenerate: Boolean,
    ): UiMedia.Image =
        withContext(PlatformDispatchers.IO) {
            if (!config.configured) throw ImageTranslationException(ImageTranslationError.NotConfigured)
            val job = currentCoroutineContext().job
            val ownerId =
                (owner?.toString() ?: (media.proxy as? ProxyRef.Account)?.accountKey?.toString().orEmpty())
                    .encodeUtf8()
                    .sha256()
                    .hex()
            mutex.withLock {
                cleanStaleSessions()
                if (session in closedSessions) error("Image translation session is closed")
                active[job] = session to ownerId
            }
            val work = sessions / session / Uuid.random().toString()
            try {
                val ticket = cache.ticket(ownerId)
                fs.createDirectories(work)
                withTimeout(180_000) {
                    permits.withPermit {
                        val original = work / "original"
                        if (media.isRemote) {
                            sourceClient(media.proxy).use { source ->
                                source.prepareGet(media.urlForDownload) { applyMedia(media) }.execute { response ->
                                    if (response.status.value !in 200..299) throw ImageTranslationException(ImageTranslationError.Network)
                                    download(response.bodyAsChannel(), original)
                                }
                            }
                        } else {
                            copyTranslationFile(
                                fs,
                                imageTranslationFilePath(media.urlForDownload).toPath(),
                                original,
                            )
                        }
                        val info = inspectStaticImage(original)
                        val access =
                            (
                                media.customHeaders.orEmpty().entries.sortedBy { it.key.lowercase() }.joinToString("\n") {
                                    it.key.lowercase() +
                                        ":" +
                                        it.value
                                } +
                                    "|" +
                                    media.proxy
                            ).encodeUtf8()
                                .sha256()
                                .hex()
                        val key =
                            listOf(ownerId, access, translationFileHash(fs, original), target, config.fingerprint())
                                .joinToString("|")
                                .encodeUtf8()
                                .sha256()
                                .hex()
                        val identified = owner != null || media.proxy is ProxyRef.Account || media.customHeaders.isNullOrEmpty()
                        val output = work / "translated"
                        var resultInfo: TranslationImageInfo? =
                            if (!regenerate && identified) {
                                cacheOrNull { cache.read(key, output) }
                            } else {
                                null
                            }
                        if (resultInfo == null) {
                            val input =
                                ImageTranslationInput(
                                    publicUrl =
                                        media.urlForDownload.takeIf {
                                            media.isRemote && media.customHeaders.isNullOrEmpty() &&
                                                Url(it).user.isNullOrEmpty() && Url(it).password.isNullOrEmpty()
                                        },
                                    media =
                                        dev.dimension.flare.common.UploadMedia.fromSource(
                                            name = "original",
                                            mimeType = info.mimeType,
                                            size = fs.metadata(original).size,
                                            openSource = { fs.source(original) },
                                        ),
                                )
                            val adapter =
                                adapters.firstOrNull { it.provider == config.provider }
                                    ?: throw ImageTranslationException(ImageTranslationError.NotConfigured)
                            when (val result = adapter.translate(input, config, target)) {
                                is ImageTranslationOutput.Encoded -> {
                                    val bytes =
                                        result.data
                                            .removePrefix("data:")
                                            .let { value ->
                                                if (value.contains(";base64,")) value.substringAfter(";base64,") else value
                                            }.decodeBase64() ?: throw ImageTranslationException(ImageTranslationError.InvalidImage)
                                    if (bytes.size.toLong() > MAX_WORKING_BYTES) {
                                        throw ImageTranslationException(ImageTranslationError.UnsupportedInput)
                                    }
                                    fs.write(output) { write(bytes) }
                                }

                                is ImageTranslationOutput.Remote -> {
                                    val url = runCatching { Url(result.url) }.getOrNull()
                                    if (url == null || url.protocol.name !in listOf("http", "https") || !url.user.isNullOrEmpty() ||
                                        !url.password.isNullOrEmpty()
                                    ) {
                                        throw ImageTranslationException(ImageTranslationError.InvalidImage)
                                    }
                                    // No provider or source credentials are attached to the returned asset URL.
                                    mediaHttpClient(ProxyRef.Global).use { downloadClient ->
                                        downloadClient.prepareGet(result.url).execute { response ->
                                            if (response.status.value !in 200..299) {
                                                throw ImageTranslationException(ImageTranslationError.Network)
                                            }
                                            download(response.bodyAsChannel(), output)
                                        }
                                    }
                                }
                            }
                            resultInfo = inspectStaticImage(output)
                            currentCoroutineContext().ensureActive()
                            if (identified) cacheOrNull { cache.put(ticket, key, output, resultInfo) }
                        }
                        val finalInfo = requireNotNull(resultInfo)
                        val originalName =
                            dev.dimension.flare.common.MediaFileNamePolicy
                                .rawMediaFileName(media)
                                .substringBeforeLast('.')
                        val file =
                            work /
                                (
                                    originalName.take(40) + "_translated_" + target.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32) + "_" +
                                        work.name +
                                        "." +
                                        finalInfo.extension
                                )
                        fs.atomicMove(output, file)
                        currentCoroutineContext().ensureActive()
                        val fileUrl = imageTranslationFileUrl(file.toString())
                        media.copy(
                            url = fileUrl,
                            previewUrl = fileUrl,
                            width = finalInfo.width.toFloat(),
                            height = finalInfo.height.toFloat(),
                            customHeaders = null,
                            proxy = ProxyRef.Unbound,
                        )
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    runCatching { fs.delete(work / "original", mustExist = false) }
                    mutex.withLock { active.remove(job) }
                }
            }
        }

    actual suspend fun closeSession(session: String) {
        val jobs =
            mutex.withLock {
                closedSessions.add(session)
                active.filterValues { it.first == session }.keys.toList()
            }
        jobs.forEach { it.cancelAndJoin() }
        withContext(PlatformDispatchers.IO) { fs.deleteRecursively(sessions / session, mustExist = false) }
    }

    actual suspend fun clear() =
        withContext(PlatformDispatchers.IO) {
            mutex.withLock { cleanStaleSessions() }
            cache.clear()
        }

    actual suspend fun size(): Long =
        withContext(PlatformDispatchers.IO) {
            mutex.withLock { cleanStaleSessions() }
            cache.size()
        }

    actual suspend fun removeOwner(owner: MicroBlogKey) {
        val ownerId =
            owner
                .toString()
                .encodeUtf8()
                .sha256()
                .hex()
        val jobs =
            withContext(PlatformDispatchers.IO) {
                mutex.withLock {
                    cleanStaleSessions()
                    active.filterValues { it.second == ownerId }.keys.toList()
                }
            }
        jobs.forEach { it.cancelAndJoin() }
        withContext(PlatformDispatchers.IO) { cache.clear(ownerId) }
    }

    private fun cleanStaleSessions() {
        if (!cleanedSessions) {
            fs.deleteRecursively(sessions, mustExist = false)
            cleanedSessions = true
        }
    }

    private fun inspectStaticImage(file: Path): TranslationImageInfo {
        fs.source(file).buffer().use { source ->
            if (source.request(8) && source.readByteString(8).hex() == "89504e470d0a1a0a") {
                // Some platform decoders expose APNG as a single PNG frame.
                while (source.request(8)) {
                    val size = source.readInt().toLong() and 0xffffffffL
                    val type = source.readUtf8(4)
                    if (type == "acTL") throw ImageTranslationException(ImageTranslationError.UnsupportedInput)
                    if (size > MAX_WORKING_BYTES) {
                        throw ImageTranslationException(ImageTranslationError.InvalidImage)
                    }
                    try {
                        source.skip(size + 4)
                    } catch (_: okio.EOFException) {
                        throw ImageTranslationException(ImageTranslationError.InvalidImage)
                    }
                    if (type == "IEND") break
                }
            }
        }
        return inspectTranslationImage(file.toString())
    }

    private suspend fun download(
        channel: io.ktor.utils.io.ByteReadChannel,
        destination: Path,
    ) {
        val buffer = ByteArray(64 * 1024)
        fs.sink(destination).buffer().use { output ->
            var total = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = channel.readAvailable(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_WORKING_BYTES) throw ImageTranslationException(ImageTranslationError.UnsupportedInput)
                output.write(buffer, 0, read)
            }
            channel.closedCause?.let { throw it }
        }
    }

    private suspend fun <T> cacheOrNull(block: suspend () -> T): T? =
        try {
            block()
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

    private companion object {
        const val MAX_WORKING_BYTES = 512L * 1024 * 1024
    }
}
