package dev.dimension.flare.common

import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.asCoilImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.github.penfeizhou.animation.apng.APNGDrawable
import com.github.penfeizhou.animation.apng.decode.APNGParser

internal class AnimatedPngDecoder(private val source: ImageSource) : Decoder {
    @OptIn(ExperimentalCoilApi::class)
    override suspend fun decode(): DecodeResult {
        return DecodeResult(
            image = APNGDrawable.fromFile(source.file().toString()).asCoilImage(),
            isSampled = false,
        )
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            return if (APNGParser.isAPNG(result.source.file().toString())) {
                AnimatedPngDecoder(result.source)
            } else {
                null
            }
        }
    }
}
