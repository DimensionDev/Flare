package dev.dimension.flare.data.repository

import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.FileType
import okio.Path.Companion.toPath

internal actual fun draftFileItem(
    path: String,
    name: String?,
    type: FileType,
): FileItem {
    val filePath = path.toPath()
    return FileItem(
        name = name ?: filePath.name,
        path = path,
        type = type,
    )
}
