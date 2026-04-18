package dev.dimension.flare.data.io

import dev.dimension.flare.data.database.APP_GROUP_ID
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

internal class ApplePlatformPathProducer : PlatformPathProducer {
    override fun dataStoreFile(fileName: String): Path = "${documentDirectory()}/$fileName".toPath()

    override fun draftMediaFile(
        groupId: String,
        fileName: String,
    ): Path {
        val dir = "${appGroupDirectory()}/draft_media/$groupId"
        ensureDirectory(dir)
        return "$dir/$fileName".toPath()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun documentDirectory(): String {
        val documentDirectory: NSURL? =
            NSFileManager.defaultManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = false,
                error = null,
            )
        return requireNotNull(documentDirectory?.path)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun appGroupDirectory(): String {
        val containerUrl =
            NSFileManager.defaultManager
                .containerURLForSecurityApplicationGroupIdentifier(APP_GROUP_ID)
                ?: error("App Group not configured: $APP_GROUP_ID")
        return requireNotNull(containerUrl.path)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun ensureDirectory(path: String) {
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(path)) {
            fileManager.createDirectoryAtPath(
                path = path,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }
    }
}
