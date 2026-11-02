package dev.dimension.flare.ui.screen.compose

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.dimension.flare.common.MimeTypes
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.ui.model.UiDraftMedia
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal class ComposeMediaState(
    private val context: Context,
    private val config: () -> ComposeConfig.Media?,
    initialMedias: List<Uri> = emptyList(),
    private val scope: CoroutineScope,
    private val imagesAllowed: () -> Boolean = { true },
    private val onError: (Throwable) -> Unit = {},
    private val importImage: suspend (Uri) -> File = { copyComposeImage(context, it) },
) {
    private class Entry(
        val media: MediaData? = null,
        val file: File? = null,
        val source: Any? = null,
    )

    private var entries by mutableStateOf(initialMedias.map { Entry(createMedia(it)) })
    private val pending = mutableMapOf<Entry, Job>()
    private val retainedFiles = mutableSetOf<File>()
    private var importError: Throwable? = null
    private var closed = false
    private var generation = 0
    private var submitting = false
    private var submission: Job? = null
    private var sensitive by mutableStateOf(false)
    val medias get() = entries.mapNotNull { it.media }.toImmutableList()
    val hasPendingImports get() = entries.any { it.media == null }
    val isMediaSensitive get() = sensitive
    val canAddMedia get() = entries.size < (config()?.maxCount ?: 0)
    val canSensitive get() = config()?.canSensitive == true
    val enabled get() = (config()?.maxCount ?: 0) > 0
    val enableAltText get() = altTextMaxLength > 0
    val altTextMaxLength get() = config()?.altTextMaxLength ?: 0
    private val remaining get() = ((config()?.maxCount ?: 0) - entries.size).coerceAtLeast(0)

    fun addMedia(uris: List<Uri>) {
        if (closed || !imagesAllowed()) return
        entries = entries +
            uris
                .distinct()
                .filterNot { uri -> entries.any { it.media?.uri == uri } }
                .take(remaining)
                .map { Entry(createMedia(it)) }
    }

    fun importImages(
        uris: List<Uri>,
        source: Any? = null,
    ) {
        if (closed || !imagesAllowed()) return
        val accepted = uris.take(remaining)
        if (accepted.isEmpty()) return
        if (pending.isEmpty()) importError = null
        accepted.forEach { uri ->
            // Retain the IME permission grant until its image copy finishes.
            val entry = Entry(source = source)
            entries = entries + entry
            val previous = pending.values.lastOrNull()
            val job =
                scope.launch(start = CoroutineStart.LAZY) {
                    var file: File? = null
                    try {
                        previous?.join()
                        file = importImage(uri)
                        val index = entries.indexOf(entry)
                        if (!closed && imagesAllowed() && index in 0 until (config()?.maxCount ?: 0)) {
                            entries = entries.map { if (it === entry) Entry(createMedia(Uri.fromFile(file)), file) else it }
                            file = null
                        } else {
                            entries = entries - entry
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        if (!closed && entry in entries) {
                            importError = error
                            onError(error)
                        }
                    } finally {
                        file?.delete()
                        entries = entries - entry
                        pending.remove(entry)
                    }
                }
            pending[entry] = job
            job.start()
        }
    }

    fun dispatchWhenReady(
        dispatch: ((Boolean) -> Unit) -> Unit,
        onDispatched: (Boolean) -> Unit,
    ) {
        if (closed || submitting) return
        submitting = true
        val generation = this.generation
        submission =
            scope.launch {
                try {
                    while (pending.isNotEmpty()) pending.values.toList().forEach { it.join() }
                    if (closed || importError != null) {
                        submitting = false
                        onDispatched(false)
                        return@launch
                    }
                    val files = entries.mapNotNull { it.file }
                    retainedFiles.addAll(files)
                    var completed = false
                    val complete: (Boolean) -> Unit = { success ->
                        if (!completed) {
                            completed = true
                            retainedFiles.removeAll(files.toSet())
                            files.filter { file -> closed || entries.none { it.file == file } }.forEach(File::delete)
                            if (generation == this@ComposeMediaState.generation) {
                                submitting = false
                                onDispatched(success)
                            }
                        }
                    }
                    try {
                        dispatch(complete)
                    } catch (error: Exception) {
                        complete(false)
                        if (error is CancellationException) throw error
                        onError(error)
                    }
                } catch (cancelled: CancellationException) {
                    if (generation == this@ComposeMediaState.generation) submitting = false
                    throw cancelled
                }
            }
    }

    fun replaceMedias(medias: List<UiDraftMedia>) {
        clear()
        entries = medias.map { Entry(createMedia(Uri.parse(it.cachePath), TextFieldState(it.altText.orEmpty()))) }
    }

    fun removeMedia(uri: Uri) {
        entries.filter { it.media?.uri == uri }.forEach { release(it.file) }
        entries = entries.filterNot { it.media?.uri == uri }
        if (entries.isEmpty()) sensitive = false
    }

    fun setMediaSensitive(value: Boolean) {
        sensitive = value
    }

    fun close() {
        closed = true
        clear()
    }

    private fun clear() {
        generation++
        submission?.cancel()
        submission = null
        submitting = false
        pending.values.toList().forEach(Job::cancel)
        pending.clear()
        entries.forEach { release(it.file) }
        entries = emptyList()
        importError = null
    }

    private fun release(file: File?) {
        if (file !in retainedFiles) file?.delete()
    }

    private fun createMedia(
        uri: Uri,
        textState: TextFieldState = TextFieldState(),
    ) = MediaData(uri, textState, context.contentResolver.getType(uri)?.startsWith("video/") == true)
}

internal suspend fun copyComposeImage(
    context: Context,
    uri: Uri,
): File {
    var file: File? = null
    try {
        return withContext(Dispatchers.IO) {
            require(uri.scheme == "content" || uri.scheme == "file")
            val destination = File.createTempFile("compose-", ".image", context.cacheDir)
            file = destination
            checkNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(destination.path, bounds)
            check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unable to read image" }
            val extension =
                checkNotNull(
                    MimeTypes.extensionFor(bounds.outMimeType)
                        ?: bounds.outMimeType?.let(MimeTypeMap.getSingleton()::getExtensionFromMimeType),
                )
            val named = File(destination.parentFile, "${destination.nameWithoutExtension}.$extension")
            check(destination.renameTo(named)) { "Unable to store image" }
            file = named
            named
        }
    } catch (error: Throwable) {
        file?.delete()
        throw error
    }
}

internal data class MediaData(
    val uri: Uri,
    val textState: TextFieldState = TextFieldState(),
    val isVideo: Boolean,
) {
    val url = uri.toString()
}
