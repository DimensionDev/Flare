package dev.dimension.flare

import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.FileType
import okio.Path

internal expect fun createTestRootPath(): Path

internal expect fun deleteTestRootPath(path: Path)

internal expect fun createTestFileItem(
    root: Path,
    name: String?,
    bytes: ByteArray,
    type: FileType,
): FileItem
