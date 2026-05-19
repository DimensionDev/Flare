package dev.dimension.flare.data.draft

import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.FileType
import okio.Path.Companion.toPath

internal actual fun draftFileItem(
    path: String,
    name: String?,
    type: FileType,
    readBytes: suspend () -> ByteArray,
): FileItem =
    FileItem(
        name = name ?: path.toPath().name,
        type = type,
        loader = readBytes,
    )
