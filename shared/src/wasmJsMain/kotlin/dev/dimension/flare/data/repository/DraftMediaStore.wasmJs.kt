package dev.dimension.flare.data.repository

import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.FileType

internal actual fun draftFileItem(
    path: String,
    name: String?,
    type: FileType,
    loader: suspend () -> ByteArray,
): FileItem =
    FileItem(
        name = name,
        type = type,
        loader = loader,
    )
