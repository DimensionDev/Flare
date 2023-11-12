package dev.dimension.flare

import android.app.Application
import android.os.Build.VERSION.SDK_INT
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import dev.dimension.flare.common.AnimatedPngDecoder
import dev.dimension.flare.common.AnimatedWebPDecoder
import dev.dimension.flare.di.androidModule
import dev.dimension.flare.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@App)
            modules(appModule() + androidModule)
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
                add(SvgDecoder.Factory())
                add(AnimatedWebPDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
