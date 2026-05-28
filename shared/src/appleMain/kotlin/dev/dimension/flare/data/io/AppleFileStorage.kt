package dev.dimension.flare.data.io

import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import org.koin.core.annotation.Single
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@Single(binds = [FileStorage::class])
internal class AppleFileStorage :
    OkioFileStorage(
        fileSystem = FileSystem.SYSTEM,
        root = fileDirectory().toPath(),
    ) {
    companion object {
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
}
