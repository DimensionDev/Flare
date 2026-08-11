package dev.dimension.flare.common

public actual class FileItem internal constructor(
    private val loader: suspend () -> ByteArray,
    public actual val name: String?,
    public actual val type: FileType,
    public actual val mimeType: String? = null,
) {
    public constructor(
        name: String?,
        data: ByteArray,
        type: FileType,
        mimeType: String? = null,
    ) : this({ data }, name, type, mimeType)

    public actual suspend fun readBytes(): ByteArray = loader()
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
        name = name,
        type = type,
        mimeType = mimeType,
    )
