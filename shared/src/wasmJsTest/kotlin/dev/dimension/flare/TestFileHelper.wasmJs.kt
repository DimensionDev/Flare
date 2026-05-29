package dev.dimension.flare

import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.FileType
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.uuid.Uuid

internal actual fun createTestRootPath(): Path = "/draft-media-store-test-${Uuid.random()}".toPath()

internal actual fun deleteTestRootPath(path: Path) = Unit

internal actual fun createTestFileSystem(): FileSystem = InMemoryTestFileSystem()

internal actual fun createTestFileItem(
    root: Path,
    name: String?,
    bytes: ByteArray,
    type: FileType,
): FileItem =
    FileItem(
        name = name,
        data = bytes,
        type = type,
    )
