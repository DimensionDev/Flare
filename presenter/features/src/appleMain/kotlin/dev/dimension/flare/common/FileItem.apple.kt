package dev.dimension.flare.common

import okio.FileSystem
import okio.Path.Companion.toPath

public actual class FileItem internal constructor(
    internal actual val name: String?,
    private val loader: suspend () -> ByteArray,
    internal actual val type: FileType,
    internal actual val mimeType: String? = null,
) {
    public constructor(
        name: String?,
        data: ByteArray,
        type: FileType,
        mimeType: String? = null,
    ) : this(
        name = name,
        loader = { data },
        type = type,
        mimeType = mimeType,
    )

    internal constructor(
        name: String?,
        path: String,
        type: FileType,
        mimeType: String? = null,
    ) : this(
        name = name,
        loader = { FileSystem.SYSTEM.read(path.toPath()) { readByteArray() } },
        type = type,
        mimeType = mimeType,
    )

    internal actual suspend fun readBytes(): ByteArray = loader()
}
