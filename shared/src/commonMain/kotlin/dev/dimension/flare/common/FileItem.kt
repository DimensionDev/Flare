package dev.dimension.flare.common

import kotlin.native.HiddenFromObjC

public expect class FileItem {
    public suspend fun readBytes(): ByteArray

    @HiddenFromObjC
    public suspend fun uploadMedia(): UploadMedia

    public val name: String?
    public val type: FileType
    public val mimeType: String?
}
