package dev.dimension.flare

import dev.dimension.flare.data.io.FileItem
import dev.dimension.flare.data.io.FileType
import okio.Path

internal expect fun createTestRootPath(): Path

internal expect fun deleteTestRootPath(path: Path)

internal expect fun createTestFileItem(
    root: Path,
    name: String?,
    bytes: ByteArray,
    type: FileType,
): FileItem
