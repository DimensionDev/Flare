package dev.dimension.flare.data.io

import dev.dimension.flare.common.FileSystemUtilsExt
import okio.Path
import okio.Path.Companion.toOkioPath

internal class JvmPlatformPathProducer : PlatformPathProducer {
    override fun dataStoreFile(fileName: String): Path = FileSystemUtilsExt.flareDirectory().toOkioPath().resolve(fileName)

    override fun draftMediaFile(
        groupId: String,
        fileName: String,
    ): Path =
        FileSystemUtilsExt
            .flareDirectory()
            .toOkioPath()
            .resolve("draft_media")
            .resolve(groupId)
            .resolve(fileName)
}
