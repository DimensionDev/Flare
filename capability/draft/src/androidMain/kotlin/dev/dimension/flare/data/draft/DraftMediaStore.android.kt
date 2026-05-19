package dev.dimension.flare.data.draft

import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.FileType

internal actual fun draftFileItem(
    @Suppress("UNUSED_PARAMETER")
    path: String,
    name: String?,
    type: FileType,
    readBytes: suspend () -> ByteArray,
): FileItem =
    FileItem(
        name = name,
        type = type,
        loader = readBytes,
    )
