package dev.dimension.flare.common

import okio.Buffer
import okio.Source

public actual class FileItem internal constructor(
    private val loader: suspend () -> ByteArray,
    private val data: ByteArray? = null,
    public actual val name: String?,
    public actual val type: FileType,
    public actual val mimeType: String? = null,
) {
    public constructor(
        name: String?,
        data: ByteArray,
        type: FileType,
        mimeType: String? = null,
    ) : this({ data }, data, name, type, mimeType)

    public actual suspend fun readBytes(): ByteArray = loader()

    public actual fun openSource(): Source = data?.let { Buffer().write(it) } ?: error("This file cannot be streamed")

    public actual suspend fun size(): Long = data?.size?.toLong() ?: loader().size.toLong()
}

public actual fun fileItemFromStorage(
    path: String,
    name: String?,
    type: FileType,
    mimeType: String?,
    loader: suspend () -> ByteArray,
): FileItem =
    FileItem(
        loader = loader,
        data = null,
        name = name,
        type = type,
        mimeType = mimeType,
    )
