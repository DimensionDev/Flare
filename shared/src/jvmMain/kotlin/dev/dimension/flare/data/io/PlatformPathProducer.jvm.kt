package dev.dimension.flare.data.io

import dev.dimension.flare.common.FileSystemUtilsExt
import okio.Path
import okio.Path.Companion.toOkioPath

public actual class PlatformPathProducer {
    public actual fun dataStoreFile(fileName: String): Path = FileSystemUtilsExt.flareDirectory().toOkioPath().resolve(fileName)
}
