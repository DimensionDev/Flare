package dev.dimension.flare.data.repository

import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.FileType
import dev.dimension.flare.common.fileItemFromStorage

internal actual fun draftFileItem(
    path: String,
    name: String?,
    type: FileType,
    mimeType: String?,
    loader: suspend () -> ByteArray,
): FileItem =
    fileItemFromStorage(
        path = path,
        name = name,
        type = type,
        mimeType = mimeType,
        loader = loader,
    )
