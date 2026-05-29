package dev.dimension.flare.shared.image

import org.koin.core.annotation.Single

@Single(binds = [ImageCompressor::class])
internal class WebImageCompressor : ImageCompressor {
    override suspend fun compress(
        imageBytes: ByteArray,
        maxSize: Long,
        maxDimensions: Pair<Int, Int>,
    ): ByteArray = imageBytes
}
