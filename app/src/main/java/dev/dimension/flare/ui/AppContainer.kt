package dev.dimension.flare.ui

import android.os.Build
import androidx.compose.animation.AnimatedContent
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
import com.ramcosta.composedestinations.DestinationsNavHost
import dev.dimension.flare.common.AnimatedPngDecoder
import dev.dimension.flare.common.AnimatedWebPDecoder
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.splash.SplashPresenter
import dev.dimension.flare.ui.presenter.splash.SplashType
import dev.dimension.flare.ui.screen.NavGraphs
import dev.dimension.flare.ui.screen.home.HomeScreen
import dev.dimension.flare.ui.screen.splash.SplashScreen
import org.koin.compose.koinInject

@OptIn(ExperimentalCoilApi::class)
@Composable
fun AppContainer() {
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
    val state by producePresenter("AppContainer") {
        SplashPresenter({}, {}).invoke()
    }
    val settingsRepository = koinInject<SettingsRepository>()
    val appearanceSettings by settingsRepository.appearanceSettings.collectAsState(
        AppearanceSettings(),
    )
    CompositionLocalProvider(
        LocalAppearanceSettings provides appearanceSettings,
    ) {
        AnimatedContent(targetState = state, label = "AppContainer") {
            when (it) {
                SplashType.Splash -> SplashScreen()
                SplashType.Login -> DestinationsNavHost(NavGraphs.entry)
                is SplashType.Home -> HomeScreen()
            }
        }
    }
}
