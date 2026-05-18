package dev.dimension.flare.media

public interface ImageCompressor {
    public suspend fun compress(
        imageBytes: ByteArray,
        maxSize: Long,
        maxDimensions: Pair<Int, Int>,
    ): ByteArray
}
