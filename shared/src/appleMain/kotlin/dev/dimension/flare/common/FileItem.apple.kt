package dev.dimension.flare.common

import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.Source
import okio.buffer
import okio.use
import kotlin.native.HiddenFromObjC

public actual class FileItem internal constructor(
    public actual val name: String?,
    private val source: () -> Source,
    private val size: () -> Long?,
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
        source = { Buffer().write(data) },
        size = { data.size.toLong() },
        type = type,
        mimeType = mimeType,
    )

    public constructor(
        name: String?,
        path: String,
        type: FileType,
        mimeType: String? = null,
    ) : this(
        name = name,
        source = { FileSystem.SYSTEM.source(path.toPath()) },
        size = { FileSystem.SYSTEM.metadata(path.toPath()).size },
        type = type,
        mimeType = mimeType,
    )

    public actual suspend fun readBytes(): ByteArray = source().buffer().use { it.readByteArray() }

    @HiddenFromObjC
    public actual suspend fun uploadMedia(): UploadMedia = UploadMedia.fromSource(name, mimeType, size(), source)
}
