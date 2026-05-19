package dev.dimension.flare.common

import okio.FileSystem
import okio.Path.Companion.toPath

public actual class FileItem internal constructor(
    public actual val name: String?,
    private val loader: suspend () -> ByteArray,
    public actual val type: FileType,
    public actual val mimeType: String? = null,
) {
    public constructor(
        name: String?,
        data: ByteArray,
        type: FileType,
        mimeType: String? = null,
    ) : this(name, { data }, type, mimeType)

    public actual constructor(
        name: String?,
        type: FileType,
        loader: suspend () -> ByteArray,
        mimeType: String?,
    ) : this(name, loader, type, mimeType)

    public constructor(
        name: String?,
        path: String,
        type: FileType,
        mimeType: String? = null,
    ) : this(name, { FileSystem.SYSTEM.read(path.toPath()) { readByteArray() } }, type, mimeType)

    public actual suspend fun readBytes(): ByteArray = loader()
}
