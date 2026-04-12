package dev.dimension.flare.common

public actual class FileItem {
    public actual suspend fun readBytes(): ByteArray {
        TODO("Not yet implemented")
    }

    public actual val name: String?
        get() = TODO("Not yet implemented")
    public actual val type: FileType
        get() = TODO("Not yet implemented")
}
