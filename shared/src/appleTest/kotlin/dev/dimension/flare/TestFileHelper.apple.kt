package dev.dimension.flare

import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.FileType
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import platform.Foundation.NSTemporaryDirectory
import kotlin.uuid.Uuid

internal actual fun createTestRootPath(): Path = "${NSTemporaryDirectory()}draft-media-store-test-${Uuid.random()}".toPath()

internal actual fun deleteTestRootPath(path: Path) {
    if (FileSystem.SYSTEM.exists(path)) {
        FileSystem.SYSTEM.deleteRecursively(path)
    }
}

internal actual fun createTestFileItem(
    root: Path,
    name: String?,
    bytes: ByteArray,
    type: FileType,
): FileItem {
    val path = root.resolve("source_${Uuid.random()}.bin")
    FileSystem.SYSTEM.createDirectories(path.parent!!)
    FileSystem.SYSTEM.write(path) {
        write(bytes)
    }
    return FileItem(
        name = name,
        data = bytes,
        type = type,
    )
}
