package dev.dimension.flare.data.repository

import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDataSource
import dev.dimension.flare.data.io.FileStorage
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.network.UgoiraDownloader
import dev.dimension.flare.data.network.ugoiraHttpClient
import dev.dimension.flare.media.UgoiraAnimation
import dev.dimension.flare.media.UgoiraFrame
import dev.dimension.flare.media.UgoiraMetadata
import dev.dimension.flare.ui.model.UiMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okio.ByteString.Companion.encodeUtf8
import okio.Path
import org.koin.core.annotation.Single
import kotlin.time.Clock

@Single
internal fun ugoiraRepository(
    fileStorage: FileStorage,
    scope: CoroutineScope,
    accountRepository: AccountRepository,
): UgoiraRepository {
    val storage = requireNotNull(fileStorage as? OkioFileStorage) { "Ugoira requires native file storage" }
    val client = ugoiraHttpClient()
    scope.coroutineContext[Job]?.invokeOnCompletion { client.close() }
    return UgoiraRepository(storage, scope, UgoiraDownloader(storage.fileSystem, client)) { media ->
        val account = requireNotNull(accountRepository.find(media.accountKey)) { "Pixiv account is unavailable" }
        val dataSource = accountRepository.getOrCreateDataSource(account) as GalleryDataSource
        dataSource.ugoiraMetadata(media.statusKey)
    }
}

/** Owns complete frame sequences and their playback/export leases. */
internal actual class UgoiraRepository(
    private val fileStorage: OkioFileStorage,
    private val scope: CoroutineScope,
    private val downloader: UgoiraDownloader,
    private val metadata: suspend (UiMedia.Ugoira) -> UgoiraMetadata,
) {
    private val fileSystem = fileStorage.fileSystem
    private val root = fileStorage.dataStoreFile("ugoira_cache")

    // ponytail: serialize cache maintenance; use per-entry locks if disk cleanup becomes a bottleneck.
    private val mutex = Mutex()
    private val entries = mutableMapOf<String, Entry>()
    private var cleaned = false

    private class Entry(
        val job: Deferred<Result<UgoiraAnimation>>,
        val progress: MutableStateFlow<Float>,
        var users: Int = 0,
        var invalidated: Boolean = false,
        var removeOnRelease: Boolean = false,
    )

    @Serializable
    private data class Manifest(
        val frames: List<UgoiraFrame>,
        val quality: String,
        val createdAt: Long,
    )

    actual suspend fun load(
        media: UiMedia.Ugoira,
        onProgress: (Float) -> Unit,
    ): UgoiraAnimation =
        coroutineScope {
            val key = "${media.accountKey}|${media.statusKey}|${media.originalFrameUrl}".encodeUtf8().sha256().hex()
            val entry =
                mutex.withLock {
                    check(entries[key]?.invalidated != true) { "Ugoira cache is in use; retry after playback or export releases it" }
                    if (!cleaned) {
                        withContext(PlatformDispatchers.IO) {
                            fileSystem
                                .listOrNull(root)
                                .orEmpty()
                                .filter { it.name.endsWith(".part") }
                                .forEach(fileSystem::deleteRecursively)
                        }
                        cleaned = true
                    }
                    entries
                        .getOrPut(key) {
                            val progress = MutableStateFlow(0f)
                            // A failed transfer must not cancel the application scope or other downloads.
                            val job = scope.async { tryRun { prepare(key, media, progress) } }
                            Entry(job, progress)
                        }.also { it.users++ }
                }
            val observer = launch { entry.progress.collect(onProgress) }
            try {
                entry.job.await().getOrThrow()
            } catch (cause: Throwable) {
                releaseKey(key)
                throw cause
            } finally {
                observer.cancel()
            }
        }

    actual suspend fun invalidate(animation: UgoiraAnimation) {
        mutex.withLock {
            entries[animation.key]?.let {
                it.invalidated = true
                it.removeOnRelease = true
            }
        }
    }

    actual fun release(animation: UgoiraAnimation) = releaseKey(animation.key)

    private fun releaseKey(key: String) {
        scope.launch {
            tryRun {
                mutex.withLock {
                    val entry = entries[key] ?: return@withLock
                    if (--entry.users == 0) {
                        entry.job.cancelAndJoin()
                        entries.remove(key)
                        if (entry.removeOnRelease) fileSystem.deleteRecursively(root / key, mustExist = false)
                        trim()
                    }
                }
            }
        }
    }

    actual suspend fun clear() =
        withContext(PlatformDispatchers.IO) {
            mutex.withLock {
                entries.values.forEach { it.removeOnRelease = true }
                fileSystem
                    .listOrNull(root)
                    .orEmpty()
                    .filter { it.name.removeSuffix(".part") !in entries }
                    .forEach(fileSystem::deleteRecursively)
            }
        }

    actual suspend fun size(): Long = withContext(PlatformDispatchers.IO) { directorySize(root) }

    private suspend fun prepare(
        key: String,
        media: UiMedia.Ugoira,
        progress: MutableStateFlow<Float>,
    ): UgoiraAnimation {
        val directory = root / key
        readCache(directory)?.let {
            progress.value = 1f
            return it.animation(key, directory)
        }
        val staging = root / "$key.part"
        fileSystem.deleteRecursively(staging, mustExist = false)
        fileStorage.createDirectories(staging)
        try {
            val download = downloader.download(media, metadata(media), staging) { progress.value = it }
            val manifest = Manifest(download.frames, download.quality, Clock.System.now().toEpochMilliseconds())
            fileStorage.write(staging / "manifest.json", manifest.encodeJson().encodeToByteArray())
            fileSystem.deleteRecursively(directory, mustExist = false)
            fileSystem.atomicMove(staging, directory)
            mutex.withLock { trim() }
            progress.value = 1f
            return manifest.animation(key, directory)
        } finally {
            fileSystem.deleteRecursively(staging, mustExist = false)
        }
    }

    private fun readCache(directory: Path): Manifest? {
        val manifestPath = directory / "manifest.json"
        if (!fileStorage.exists(manifestPath)) return null
        val cached = runCatching { fileStorage.read(manifestPath).decodeToString().decodeJson<Manifest>() }.getOrNull()
        if (cached == null || Clock.System.now().toEpochMilliseconds() - cached.createdAt >= CACHE_AGE ||
            runCatching { UgoiraMetadata("", cached.frames).validate() }.isFailure ||
            cached.frames.any {
                !CACHE_FRAME.matches(it.file) ||
                    (fileSystem.metadataOrNull(directory / it.file)?.size ?: 0) !in 1..UgoiraDownloader.FRAME_LIMIT
            }
        ) {
            fileSystem.deleteRecursively(directory)
            return null
        }
        fileStorage.write(manifestPath, cached.encodeJson().encodeToByteArray())
        return cached
    }

    private fun Manifest.animation(
        key: String,
        directory: Path,
    ) = UgoiraAnimation(key, frames.map { it.copy(file = (directory / it.file).toString()) }, quality)

    private fun directorySize(path: Path): Long =
        fileSystem.listOrNull(path).orEmpty().sumOf { child ->
            val metadata = fileSystem.metadataOrNull(child)
            if (metadata?.isDirectory == true) directorySize(child) else metadata?.size ?: 0
        }

    private fun trim() {
        val directories = fileSystem.listOrNull(root).orEmpty().filter { !it.name.endsWith(".part") }
        var bytes = directories.sumOf(::directorySize)
        directories.sortedBy { fileSystem.metadataOrNull(it / "manifest.json")?.lastModifiedAtMillis ?: 0 }.forEach { directory ->
            if (directory.name !in entries && bytes > UgoiraDownloader.SEQUENCE_LIMIT) {
                bytes -= directorySize(directory)
                fileSystem.deleteRecursively(directory)
            }
        }
    }

    private companion object {
        val CACHE_FRAME = Regex("[0-9]+\\.(jpg|png|gif|jpeg)")
        const val CACHE_AGE = 14L * 24 * 60 * 60 * 1000
    }
}
