package dev.dimension.flare.common

import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.decode.ImageSource
import coil.fetch.SourceResult
import coil.request.Options
import com.github.penfeizhou.animation.apng.APNGDrawable
import com.github.penfeizhou.animation.apng.decode.APNGParser

internal class AnimatedPngDecoder(
    private val source: ImageSource,
) : Decoder {
    @OptIn(ExperimentalCoilApi::class)
    override suspend fun decode(): DecodeResult =
        DecodeResult(
            drawable = APNGDrawable.fromFile(source.file().toString()),
            isSampled = false,
        )

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? =
            if (APNGParser.isAPNG(result.source.file().toString())) {
                AnimatedPngDecoder(result.source)
            } else {
                null
            }
    }
}
