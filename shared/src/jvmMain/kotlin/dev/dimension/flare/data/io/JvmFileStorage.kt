package dev.dimension.flare.data.io

import dev.dimension.flare.common.FileSystemUtilsExt
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.SYSTEM
import org.koin.core.annotation.Single

@Single(binds = [FileStorage::class])
internal class JvmFileStorage :
    OkioFileStorage(
        fileSystem = FileSystem.SYSTEM,
        root = FileSystemUtilsExt.flareDirectory().toOkioPath(),
    )
