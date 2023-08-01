package dev.dimension.flare

import android.app.Application
import android.os.Build.VERSION.SDK_INT
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.moriatsushi.koject.Koject
import com.moriatsushi.koject.android.application
import com.moriatsushi.koject.start
import dev.dimension.flare.common.AnimatedPngDecoder

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Koject.start {
            application(this@App)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(AnimatedPngDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
