package dev.dimension.flare.data.repository

import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.FileType
import okio.Path.Companion.toPath

internal actual fun draftFileItem(
    path: String,
    name: String?,
    type: FileType,
): FileItem =
    FileItem(
        name = name ?: path.toPath().name,
        type = type,
        loader = {
            defaultDraftMediaStorage().read(path.toPath())
        },
    )
