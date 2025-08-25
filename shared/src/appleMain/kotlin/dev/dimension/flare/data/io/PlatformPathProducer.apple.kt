package dev.dimension.flare.data.io

import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

public actual class PlatformPathProducer {
    public actual fun dataStoreFile(fileName: String): Path = "${fileDirectory()}/$fileName".toPath()

    @OptIn(ExperimentalForeignApi::class)
    private fun fileDirectory(): String {
        val documentDirectory: NSURL? =
            NSFileManager.defaultManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = false,
                error = null,
            )
        return requireNotNull(documentDirectory).path!!
    }
}
