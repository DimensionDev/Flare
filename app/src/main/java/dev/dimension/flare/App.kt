package dev.dimension.flare

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import coil3.video.VideoFrameDecoder
import dev.dimension.flare.common.AnimatedPngDecoder
import dev.dimension.flare.common.AnimatedWebPDecoder
import dev.dimension.flare.di.KoinHelper
import dev.dimension.flare.di.androidModule
import dev.dimension.flare.di.composeUiModule
import io.ktor.client.HttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class App :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@App)
            modules(KoinHelper.modules() + androidModule + composeUiModule)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(factory = AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(AnimatedPngDecoder.Factory())
                add(SvgDecoder.Factory())
                add(AnimatedWebPDecoder.Factory())
                add(VideoFrameDecoder.Factory())
                add(
                    KtorNetworkFetcherFactory(
                        httpClient = HttpClient(dev.dimension.flare.data.network.httpClientEngine),
                    ),
                )
            }.crossfade(true)
            .build()
}
