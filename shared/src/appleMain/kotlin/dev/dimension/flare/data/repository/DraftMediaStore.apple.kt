package dev.dimension.flare.data.repository

import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.FileType
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

internal actual fun draftFileItem(
    path: String,
    name: String?,
    type: FileType,
): FileItem {
    val filePath = path.toPath()
    val bytes = FileSystem.SYSTEM.read(filePath) { readByteArray() }
    return FileItem(
        name = name ?: filePath.name,
        data = bytes,
        type = type,
    )
}
