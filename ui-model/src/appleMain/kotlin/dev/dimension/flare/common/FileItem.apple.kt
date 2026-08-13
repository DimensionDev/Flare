package dev.dimension.flare.common

import okio.FileSystem
import okio.Buffer
import okio.Path.Companion.toPath
import okio.Source
import kotlin.native.HiddenFromObjC

public actual class FileItem internal constructor(
    public actual val name: String?,
    private val loader: suspend () -> ByteArray,
    private val sourceFactory: (() -> Source)? = null,
    private val knownSize: Long? = null,
    public actual val type: FileType,
    public actual val mimeType: String? = null,
) {
    public constructor(
        name: String?,
        data: ByteArray,
        type: FileType,
        mimeType: String? = null,
    ) : this(
        name = name,
        loader = { data },
        sourceFactory = { Buffer().write(data) },
        knownSize = data.size.toLong(),
        type = type,
        mimeType = mimeType,
    )

    internal constructor(
        name: String?,
        path: String,
        type: FileType,
        mimeType: String? = null,
    ) : this(
        name = name,
        loader = { FileSystem.SYSTEM.read(path.toPath()) { readByteArray() } },
        sourceFactory = { FileSystem.SYSTEM.source(path.toPath()) },
        knownSize = FileSystem.SYSTEM.metadataOrNull(path.toPath())?.size,
        type = type,
        mimeType = mimeType,
    )

    public actual suspend fun readBytes(): ByteArray = loader()

    public actual fun openSource(): Source = sourceFactory?.invoke() ?: error("This file cannot be streamed")

    public actual suspend fun size(): Long = knownSize ?: loader().size.toLong()
}

@HiddenFromObjC
public actual fun fileItemFromStorage(
    path: String,
    name: String?,
    type: FileType,
    mimeType: String?,
    loader: suspend () -> ByteArray,
): FileItem {
    val filePath = path.toPath()
    return FileItem(
        name = name ?: filePath.name,
        path = path,
        type = type,
        mimeType = mimeType,
    )
}
