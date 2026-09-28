package dev.dimension.flare.media

import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDataSource
import dev.dimension.flare.data.io.FileStorage
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.ui.model.UiMedia
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Url
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.openZip
import okio.use
import kotlin.time.Clock

/** Shared by playback and export. No account credentials are written to the cache. */
public actual object UgoiraStore {
    public actual val supportsPlayback: Boolean = true

    private val cache by lazy {
        UgoiraCache(
            root = koinGet<FileStorage>().dataStoreFile("ugoira_cache"),
            fs = (koinGet<FileStorage>() as OkioFileStorage).fileSystem,
            client =
                ktorClient {
                    expectSuccess = false
                    followRedirects = false
                },
            metadata = { media ->
                val accounts = koinGet<AccountRepository>()
                val account = requireNotNull(accounts.find(media.accountKey)) { "Pixiv account is unavailable" }
                val service = accounts.getOrCreateDataSource(account) as GalleryDataSource
                service.ugoiraMetadata(media.statusKey)
            },
        )
    }

    @Throws(Exception::class)
    public actual suspend fun load(
        media: UiMedia.Ugoira,
        onProgress: (Float) -> Unit,
    ): UgoiraAnimation = cache.load(media, onProgress)

    @Throws(Exception::class)
    public actual suspend fun invalidate(animation: UgoiraAnimation): Unit = cache.invalidate(animation)

    public actual fun release(animation: UgoiraAnimation): Unit = cache.release(animation)

    @Throws(Exception::class)
    public actual suspend fun clearCache(): Unit = cache.clear()

    @Throws(Exception::class)
    public actual suspend fun cacheSize(): Long = cache.size()
}

internal class UgoiraCache(
    private val root: Path,
    private val client: HttpClient,
    private val metadata: suspend (UiMedia.Ugoira) -> UgoiraMetadata,
    private val fs: FileSystem,
) {
    private val scope = CoroutineScope(SupervisorJob() + PlatformDispatchers.IO)
    private val mutex = Mutex()
    private val entries = mutableMapOf<String, Entry>()
    private val pendingClear = mutableSetOf<String>()
    private val invalid = mutableSetOf<String>()
    private var cleaned = false

    private class Entry(
        val job: Deferred<UgoiraAnimation>,
        val progress: MutableStateFlow<Float>,
        var users: Int = 0,
    )

    @Serializable
    private data class Manifest(
        val frames: List<UgoiraFrame>,
        val quality: String,
        val createdAt: Long,
    )

    suspend fun load(
        media: UiMedia.Ugoira,
        onProgress: (Float) -> Unit,
    ): UgoiraAnimation =
        coroutineScope {
            val key = "${media.accountKey}|${media.statusKey}|${media.originalFrameUrl}".encodeUtf8().sha256().hex()
            val entry =
                mutex.withLock {
                    check(key !in invalid) { "Ugoira cache is in use; retry after playback or export releases it" }
                    if (!cleaned) {
                        withContext(PlatformDispatchers.IO) {
                            fs
                                .listOrNull(root)
                                .orEmpty()
                                .filter { it.name.endsWith(".part") }
                                .forEach { fs.deleteRecursively(it) }
                        }
                        cleaned = true
                    }
                    entries
                        .getOrPut(key) {
                            val progress = MutableStateFlow(0f)
                            Entry(scope.async { prepare(key, media, progress) }, progress)
                        }.also { it.users++ }
                }
            val observer = launch { entry.progress.collect(onProgress) }
            try {
                entry.job.await()
            } catch (error: Throwable) {
                releaseKey(key)
                throw error
            } finally {
                observer.cancel()
            }
        }

    suspend fun invalidate(animation: UgoiraAnimation) {
        mutex.withLock {
            if (animation.key in entries) {
                invalid.add(animation.key)
                pendingClear.add(animation.key)
            }
        }
    }

    fun release(animation: UgoiraAnimation) = releaseKey(animation.key)

    private fun releaseKey(key: String) {
        scope.launch {
            mutex.withLock {
                val entry = entries[key] ?: return@withLock
                if (--entry.users == 0) {
                    entry.job.cancelAndJoin()
                    entries.remove(key)
                    if (pendingClear.remove(key)) fs.deleteRecursively(root / key, mustExist = false)
                    invalid.remove(key)
                    trim()
                }
            }
        }
    }

    suspend fun clear() =
        withContext(PlatformDispatchers.IO) {
            mutex.withLock {
                pendingClear.addAll(entries.keys)
                fs
                    .listOrNull(root)
                    .orEmpty()
                    .filter { path ->
                        entries.keys.none { path.name == it || path.name == "$it.part" }
                    }.forEach { fs.deleteRecursively(it) }
            }
        }

    suspend fun size(): Long = withContext(PlatformDispatchers.IO) { directorySize(root) }

    private suspend fun prepare(
        key: String,
        media: UiMedia.Ugoira,
        progress: MutableStateFlow<Float>,
    ): UgoiraAnimation {
        val directory = root / key
        val manifestPath = directory / "manifest.json"
        if (fs.exists(manifestPath)) {
            val cached = runCatching { fs.read(manifestPath) { readUtf8() }.decodeJson<Manifest>() }.getOrNull()
            if (cached != null && Clock.System.now().toEpochMilliseconds() - cached.createdAt < CACHE_AGE &&
                runCatching { UgoiraMetadata("", cached.frames).validate() }.isSuccess &&
                cached.frames.all { it.file.matches(Regex("[0-9]+\\.(jpg|png|gif|jpeg)")) && fs.exists(directory / it.file) }
            ) {
                fs.write(manifestPath) { writeUtf8(cached.encodeJson()) }
                progress.value = 1f
                return cached.animation(key, directory)
            }
            fs.deleteRecursively(directory)
        }
        val info =
            metadata(media).also {
                it.validate()
                validateUrl(it.zipUrl)
            }
        val staging = root / "$key.part"
        fs.deleteRecursively(staging, mustExist = false)
        fs.createDirectories(staging)
        try {
            val original =
                media.originalFrameUrl?.takeIf {
                    Regex("https://[^/]+/img-original/.+_ugoira0\\.(jpg|png|gif)$").matches(it)
                }
            val base = (original ?: info.zipUrl.replace("/img-zip-ugoira/", "/img-original/")).substringBeforeLast("_ugoira") + "_ugoira"
            val extensions = original?.substringAfterLast('.')?.let(::listOf) ?: listOf("jpg", "png", "gif")
            var manifest: Manifest? = null
            for (extension in extensions) {
                try {
                    var bytes = 0L
                    val frames =
                        info.frames.mapIndexed { index, frame ->
                            val name = "$index.$extension"
                            bytes += download("$base$index.$extension", staging / name)
                            check(bytes <= CACHE_LIMIT) { "Ugoira exceeds the cache size limit" }
                            progress.value = (index + 1f) / info.frames.size
                            UgoiraFrame(name, frame.delayMillis)
                        }
                    manifest = Manifest(frames, "original", Clock.System.now().toEpochMilliseconds())
                    break
                } catch (_: SourceUnavailable) {
                    fs.list(staging).forEach { fs.delete(it) }
                    progress.value = 0f
                }
            }
            if (manifest == null) {
                val high = info.zipUrl.replace("_ugoira600x600", "_ugoira1920x1080")
                for (zipUrl in listOf(high, info.zipUrl).distinct()) {
                    try {
                        val zip = staging / "frames.zip"
                        download(zipUrl, zip)
                        var bytes = 0L
                        val frames =
                            fs.openZip(zip).use { archive ->
                                info.frames.mapIndexed { index, frame ->
                                    val source = "/${frame.file}".toPath()
                                    val size = requireNotNull(archive.metadata(source).size)
                                    require(
                                        size in 1..FRAME_LIMIT && bytes + size <= CACHE_LIMIT,
                                    ) { "Ugoira archive exceeds the size limit" }
                                    val name = "$index.${frame.file.substringAfterLast('.')}"
                                    archive.source(source).buffer().use { input ->
                                        fs.sink(staging / name).buffer().use { output -> output.writeAll(input) }
                                    }
                                    bytes += size
                                    progress.value = (index + 1f) / info.frames.size
                                    UgoiraFrame(name, frame.delayMillis)
                                }
                            }
                        fs.delete(zip)
                        manifest =
                            Manifest(
                                frames,
                                if (zipUrl == high &&
                                    high != info.zipUrl
                                ) {
                                    "large"
                                } else {
                                    "medium"
                                },
                                Clock.System.now().toEpochMilliseconds(),
                            )
                        break
                    } catch (_: SourceUnavailable) {
                        fs.list(staging).forEach { fs.delete(it) }
                    }
                }
            }
            val complete = checkNotNull(manifest) { "Ugoira source is unavailable" }
            fs.write(staging / "manifest.json") { writeUtf8(complete.encodeJson()) }
            fs.deleteRecursively(directory, mustExist = false)
            fs.atomicMove(staging, directory)
            mutex.withLock { trim() }
            progress.value = 1f
            return complete.animation(key, directory)
        } finally {
            fs.deleteRecursively(staging, mustExist = false)
        }
    }

    private fun Manifest.animation(
        key: String,
        directory: Path,
    ) = UgoiraAnimation(key, frames.map { it.copy(file = (directory / it.file).toString()) }, quality)

    private suspend fun download(
        url: String,
        destination: Path,
    ): Long {
        validateUrl(url)
        repeat(3) { attempt ->
            try {
                return withTimeout(60_000) {
                    client
                        .prepareGet(url) {
                            // Only the CDN Referer is needed here, never forward an account bearer token.
                            header("Referer", "https://www.pixiv.net/")
                        }.execute { response ->
                            if (response.status.value in listOf(403, 404, 410)) throw SourceUnavailable()
                            check(response.status.value == 200) { "Ugoira download failed: HTTP ${response.status.value}" }
                            val maximum = if (url.substringBefore('?').endsWith(".zip")) CACHE_LIMIT else FRAME_LIMIT
                            var total = 0L
                            val input = response.bodyAsChannel()
                            val buffer = ByteArray(64 * 1024)
                            fs.sink(destination).buffer().use { output ->
                                while (true) {
                                    val count = input.readAvailable(buffer)
                                    if (count < 0) break
                                    total += count
                                    require(total <= maximum) { "Ugoira resource exceeds the size limit" }
                                    output.write(buffer, 0, count)
                                }
                            }
                            require(total > 0) { "Empty Ugoira frame" }
                            total
                        }
                }
            } catch (error: Exception) {
                fs.delete(destination, mustExist = false)
                currentCoroutineContext().ensureActive()
                if ((error is CancellationException && error !is TimeoutCancellationException) || error is SourceUnavailable ||
                    error is IllegalArgumentException ||
                    attempt == 2
                ) {
                    throw error
                }
                delay(500L * (attempt + 1))
            }
        }
        error("Unreachable")
    }

    private fun validateUrl(value: String) {
        val url = Url(value)
        require(
            url.protocol.name == "https" && (url.host == "i.pximg.net" || url.host.endsWith(".pximg.net")),
        ) { "Invalid Pixiv media URL" }
        require(url.user.isNullOrEmpty() && url.password.isNullOrEmpty()) { "Invalid Pixiv media URL" }
    }

    private fun directorySize(path: Path): Long =
        fs.listOrNull(path).orEmpty().sumOf { child ->
            val metadata = fs.metadataOrNull(child)
            if (metadata?.isDirectory == true) directorySize(child) else metadata?.size ?: 0
        }

    private fun trim() {
        val directories = fs.listOrNull(root).orEmpty().filter { !it.name.endsWith(".part") }
        var bytes = directories.sumOf(::directorySize)
        directories.sortedBy { fs.metadataOrNull(it / "manifest.json")?.lastModifiedAtMillis ?: 0 }.forEach { directory ->
            if (directory.name !in entries && bytes > CACHE_LIMIT) {
                bytes -= directorySize(directory)
                fs.deleteRecursively(directory)
            }
        }
    }

    private class SourceUnavailable : Exception()

    private companion object {
        const val FRAME_LIMIT = 64L * 1024 * 1024
        const val CACHE_LIMIT = 512L * 1024 * 1024
        const val CACHE_AGE = 14L * 24 * 60 * 60 * 1000
    }
}
