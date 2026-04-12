package dev.dimension.flare.common

public expect class FileItem {
    public suspend fun readBytes(): ByteArray

    public val name: String?
    public val type: FileType
}
