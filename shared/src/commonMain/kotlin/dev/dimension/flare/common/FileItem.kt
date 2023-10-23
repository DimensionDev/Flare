package dev.dimension.flare.common

expect class FileItem {
    suspend fun readBytes(): ByteArray
    val name: String?
}