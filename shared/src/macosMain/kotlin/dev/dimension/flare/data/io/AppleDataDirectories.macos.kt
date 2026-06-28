package dev.dimension.flare.data.io

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory

internal actual object AppleDataDirectories {
    actual fun dataStoreRootDirectory(): String = legacyPersistentDirectory()

    actual fun databaseRootDirectory(isCache: Boolean): String =
        if (isCache) {
            legacyTemporaryDirectory()
        } else {
            legacyPersistentDirectory()
        }

    private fun legacyPersistentDirectory(): String = createDirectory(NSHomeDirectory().appendPath(".flare"))

    private fun legacyTemporaryDirectory(): String = createDirectory(NSHomeDirectory().appendPath("tmp").appendPath(".flare"))

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
