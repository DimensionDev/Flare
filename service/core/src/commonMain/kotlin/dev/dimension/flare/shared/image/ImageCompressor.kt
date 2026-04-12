package dev.dimension.flare.shared.image

public interface ImageCompressor {
    public suspend fun compress(
        imageBytes: ByteArray,
        maxSize: Long,
        maxDimensions: Pair<Int, Int>,
    ): ByteArray
}
