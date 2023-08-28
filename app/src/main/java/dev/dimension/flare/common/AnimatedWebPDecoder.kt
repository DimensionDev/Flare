package dev.dimension.flare.common

import coil.ImageLoader
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.decode.ImageSource
import coil.fetch.SourceResult
import coil.request.Options
import com.github.penfeizhou.animation.webp.WebPDrawable
import com.github.penfeizhou.animation.webp.decode.WebPParser

internal class AnimatedWebPDecoder(private val source: ImageSource) : Decoder {

    override suspend fun decode(): DecodeResult {
        return DecodeResult(
            drawable = WebPDrawable.fromFile(source.file().toString()),
            isSampled = false,
        )
    }

    class Factory : Decoder.Factory {

        override fun create(result: SourceResult, options: Options, imageLoader: ImageLoader): Decoder? {
            return if (WebPParser.isAWebP(result.source.file().toString())) {
                AnimatedWebPDecoder(result.source)
            } else {
                null
            }
        }
    }
}
