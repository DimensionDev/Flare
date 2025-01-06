package dev.dimension.flare.common

public expect class FileItem {
    internal suspend fun readBytes(): ByteArray

    internal val name: String?
}
