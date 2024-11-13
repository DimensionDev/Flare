package dev.dimension.flare.common

import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.github.penfeizhou.animation.webp.WebPDrawable
import com.github.penfeizhou.animation.webp.decode.WebPParser

internal class AnimatedWebPDecoder(
    private val source: ImageSource,
) : Decoder {
    @OptIn(ExperimentalCoilApi::class)
    override suspend fun decode(): DecodeResult =
        DecodeResult(
            image = WebPDrawable.fromFile(source.file().toString()).asImage(),
            isSampled = false,
        )

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? =
            if (WebPParser.isAWebP(result.source.file().toString())) {
                AnimatedWebPDecoder(result.source)
            } else {
                null
            }
    }
}
