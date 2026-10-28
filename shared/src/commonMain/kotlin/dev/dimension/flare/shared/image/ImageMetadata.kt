package dev.dimension.flare.shared.image

import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public data class ImageMetadata(
    public val width: Int,
    public val height: Int,
    public val orientation: Int = 1,
    public val animated: Boolean = false,
)

@HiddenFromObjC
public expect fun imageMetadata(bytes: ByteArray): ImageMetadata?
