package dev.dimension.flare.feature.plugin.runtime

import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import okio.HashingSource
import okio.Path.Companion.toPath
import okio.buffer
import okio.openZip
import okio.use

internal class PluginPackageScriptLoader(
    private val fileSystem: FileSystem,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun load(plugin: RunningPluginV1): String =
        withContext(dispatcher) {
            verify(plugin)
            val packagePath = plugin.packagePath.toPath()
            val zip = fileSystem.openZip(packagePath)
            val entry = "/plugin.js".toPath()
            val size = zip.metadataOrNull(entry)?.size ?: error("Plugin entry is missing")
            require(size in 1..MAX_SCRIPT_BYTES) { "Plugin entry is too large" }
            val bytes =
                zip.source(entry).buffer().use { source ->
                    val buffer = Buffer()
                    while (true) {
                        val remaining = MAX_SCRIPT_BYTES + 1 - buffer.size
                        require(remaining > 0) { "Plugin entry is too large" }
                        val read = source.read(buffer, remaining)
                        if (read == -1L) break
                    }
                    require(buffer.size <= MAX_SCRIPT_BYTES) { "Plugin entry is too large" }
                    buffer.readByteArray()
                }
            bytes.decodeToString(throwOnInvalidSequence = true)
        }

    private fun verify(plugin: RunningPluginV1) {
        val path = plugin.packagePath.toPath()
        val metadata = fileSystem.metadataOrNull(path) ?: throw PluginPackageChangedException()
        if (!metadata.isRegularFile || metadata.size != plugin.installed.packageSize) throw PluginPackageChangedException()
        val hashingSource = HashingSource.sha256(fileSystem.source(path))
        hashingSource.buffer().use { source ->
            val discard = Buffer()
            while (source.read(discard, COPY_BUFFER_BYTES) != -1L) discard.clear()
        }
        if (hashingSource.hash.hex() != plugin.installed.packageHash) throw PluginPackageChangedException()
    }
}

public class PluginPackageChangedException : IllegalStateException("Plugin package is missing or changed")

private const val MAX_SCRIPT_BYTES = 4L * 1024 * 1024
private const val COPY_BUFFER_BYTES = 8_192L
