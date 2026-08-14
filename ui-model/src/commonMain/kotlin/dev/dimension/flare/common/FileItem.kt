package dev.dimension.flare.common

import okio.Source
import kotlin.native.HiddenFromObjC

public expect class FileItem {
    public suspend fun readBytes(): ByteArray

    /** Opens a fresh stream. Callers own and close the returned source. */
    @HiddenFromObjC
    public fun openSource(): Source

    public suspend fun size(): Long

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
