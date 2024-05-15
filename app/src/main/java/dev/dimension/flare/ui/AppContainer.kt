package dev.dimension.flare.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

@Composable
fun FlareApp(content: @Composable () -> Unit) {
//    setSingletonImageLoaderFactory { context ->
//        ImageLoader.Builder(context)
//            .components {
//                if (Build.VERSION.SDK_INT >= 28) {
//                    add(AnimatedImageDecoder.Factory())
//                } else {
//                    add(GifDecoder.Factory())
//                }
//                add(AnimatedPngDecoder.Factory())
//                add(SvgDecoder.Factory())
//                add(AnimatedWebPDecoder.Factory())
//            }
//            .crossfade(true)
//            .build()
//    }
    val settingsRepository = koinInject<SettingsRepository>()
    val appearanceSettings by settingsRepository.appearanceSettings.collectAsState(
        AppearanceSettings(),
    )
    CompositionLocalProvider(
        LocalAppearanceSettings provides appearanceSettings,
        content = content,
    )
}
