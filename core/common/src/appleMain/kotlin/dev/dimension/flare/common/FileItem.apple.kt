package dev.dimension.flare.common

import okio.FileSystem
import okio.Path.Companion.toPath

public actual class FileItem internal constructor(
    public actual val name: String?,
    private val loader: suspend () -> ByteArray,
    public actual val type: FileType,
) {
    public constructor(
        name: String?,
        data: ByteArray,
        type: FileType,
    ) : this(
        name = name,
        loader = { data },
        type = type,
    )

    internal constructor(
        name: String?,
        path: String,
        type: FileType,
    ) : this(
        name = name,
        loader = { FileSystem.SYSTEM.read(path.toPath()) { readByteArray() } },
        type = type,
    )

    public actual suspend fun readBytes(): ByteArray = loader()
}
