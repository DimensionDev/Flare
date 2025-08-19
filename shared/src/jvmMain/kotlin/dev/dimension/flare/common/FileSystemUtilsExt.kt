package dev.dimension.flare.common

import okio.FileSystem
import org.apache.commons.lang3.SystemUtils
import java.io.File

public object FileSystemUtilsExt {
    public fun flareDirectory(): File =
        File(SystemUtils.getUserHome(), ".flare").also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }

    public fun flareCacheDirectory(): File =
        File(FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toFile(), ".flare").also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
}
