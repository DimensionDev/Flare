package dev.dimension.flare.common

public actual class FileItem(
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
    ) : this(
        loader = { data },
        name = name,
        type = type,
        mimeType = mimeType,
    )

    public actual suspend fun readBytes(): ByteArray = loader()
}
