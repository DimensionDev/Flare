package dev.dimension.flare.shared.image
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface ImageCompressor {
    public suspend fun compress(
        imageBytes: ByteArray,
        maxSize: Long,
        maxDimensions: Pair<Int, Int>,
    ): ByteArray
}
