package dev.dimension.flare.data.io

public expect class FileItem {
    public constructor(
        name: String?,
        type: FileType,
        loader: suspend () -> ByteArray,
        mimeType: String? = null,
    )

    public suspend fun readBytes(): ByteArray

    public val name: String?
    public val type: FileType
    public val mimeType: String?
}
