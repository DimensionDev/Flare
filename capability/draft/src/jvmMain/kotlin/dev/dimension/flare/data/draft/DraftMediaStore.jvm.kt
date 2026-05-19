package dev.dimension.flare.data.draft

import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.FileType
import java.io.File

internal actual fun draftFileItem(
    path: String,
    name: String?,
    type: FileType,
    @Suppress("UNUSED_PARAMETER")
    readBytes: suspend () -> ByteArray,
): FileItem =
    FileItem(
        name = name,
        type = type,
        file = File(path),
    )
