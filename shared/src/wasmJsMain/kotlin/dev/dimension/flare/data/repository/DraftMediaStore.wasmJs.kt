package dev.dimension.flare.data.repository

import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.FileType

internal actual fun draftFileItem(
    path: String,
    name: String?,
    type: FileType,
    mimeType: String?,
    loader: suspend () -> ByteArray,
): FileItem =
    FileItem(
        name = name,
        type = type,
        mimeType = mimeType,
        loader = loader,
    )
