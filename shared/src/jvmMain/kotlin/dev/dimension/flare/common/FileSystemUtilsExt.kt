package dev.dimension.flare.common

import okio.FileSystem
import org.apache.commons.lang3.SystemUtils
import java.io.File

internal object FileSystemUtilsExt {
    fun flareDirectory(): File = File(SystemUtils.getUserHome(), ".flare")

    fun flareCacheDirectory(): File = File(FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toFile(), ".flare")
}