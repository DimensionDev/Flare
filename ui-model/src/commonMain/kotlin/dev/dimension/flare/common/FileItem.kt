package dev.dimension.flare.common

import kotlin.native.HiddenFromObjC

public expect class FileItem {
    public suspend fun readBytes(): ByteArray

    public val name: String?
    public val type: FileType
    public val mimeType: String?
}

@HiddenFromObjC
public expect fun fileItemFromStorage(
    path: String,
    name: String?,
    type: FileType,
    mimeType: String? = null,
    loader: suspend () -> ByteArray,
): FileItem
