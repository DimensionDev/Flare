package dev.dimension.flare.data.draft

import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.FileType

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
        path = path,
    )
