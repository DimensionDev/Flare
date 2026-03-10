package dev.dimension.flare.data.io

import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

internal class ApplePlatformPathProducer : PlatformPathProducer {
    override fun dataStoreFile(fileName: String): Path = "${fileDirectory()}/$fileName".toPath()

    override fun draftMediaFile(
        groupId: String,
        fileName: String,
    ): Path = "${fileDirectory()}/draft_media/$groupId/$fileName".toPath()

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
