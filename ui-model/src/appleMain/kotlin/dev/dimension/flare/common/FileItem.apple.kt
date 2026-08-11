package dev.dimension.flare.common

import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.native.HiddenFromObjC

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

    public actual suspend fun readBytes(): ByteArray = loader()
}

@HiddenFromObjC
public actual fun fileItemFromStorage(
    path: String,
    name: String?,
    type: FileType,
    mimeType: String?,
    loader: suspend () -> ByteArray,
): FileItem {
    val filePath = path.toPath()
    return FileItem(
        name = name ?: filePath.name,
        path = path,
        type = type,
        mimeType = mimeType,
    )
}
