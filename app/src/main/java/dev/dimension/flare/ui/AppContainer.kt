package dev.dimension.flare.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import dev.dimension.flare.common.AnimatedPngDecoder
import dev.dimension.flare.common.AnimatedWebPDecoder
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.screen.home.HomeScreen
import org.koin.compose.koinInject

@Composable
fun AppContainer(afterInit: () -> Unit) {
    FlareApp {
        HomeScreen(
            afterInit = afterInit,
        )
    }
}

@OptIn(ExperimentalCoilApi::class)
@Composable
fun FlareApp(content: @Composable () -> Unit) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
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
    val settingsRepository = koinInject<SettingsRepository>()
    val appearanceSettings by settingsRepository.appearanceSettings.collectAsState(
        AppearanceSettings(),
    )
    CompositionLocalProvider(
        LocalAppearanceSettings provides appearanceSettings,
        content = content,
    )
}
