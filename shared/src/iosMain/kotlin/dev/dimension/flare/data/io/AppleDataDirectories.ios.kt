package dev.dimension.flare.data.io

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

internal actual object AppleDataDirectories {
    actual fun dataStoreRootDirectory(): String = documentDirectory()

    actual fun databaseRootDirectory(isCache: Boolean): String =
        createDirectory(applicationSupportDirectory().appendPath("databases"))

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
        return requireNotNull(documentDirectory).path!!
    }

    @OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
    private fun applicationSupportDirectory(): String {
        val paths =
            NSSearchPathForDirectoriesInDomains(
                NSApplicationSupportDirectory,
                NSUserDomainMask,
                true,
            )
        return paths[0] as String
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun createDirectory(path: String): String {
        val fileManager = NSFileManager.defaultManager()
        if (!fileManager.fileExistsAtPath(path)) {
            fileManager.createDirectoryAtPath(path, true, null, null)
        }
        return path
    }

    private fun String.appendPath(component: String): String = trimEnd('/') + "/$component"
}
