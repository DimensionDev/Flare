package dev.dimension.flare.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.presenter.splash.SplashPresenter
import dev.dimension.flare.ui.presenter.splash.SplashType
import dev.dimension.flare.ui.screen.home.HomeScreen
import dev.dimension.flare.ui.screen.serviceselect.ServiceSelectScreen
import dev.dimension.flare.ui.screen.splash.SplashScreen
import org.koin.compose.rememberKoinInject

@Composable
fun AppContainer() {
    val state by producePresenter("AppContainer") {
        SplashPresenter({}, {}).invoke()
    }
    val settingsRepository = rememberKoinInject<SettingsRepository>()
    val appearanceSettings by settingsRepository.appearanceSettings.collectAsState(
        AppearanceSettings(),
    )
    CompositionLocalProvider(
        LocalAppearanceSettings provides appearanceSettings,
    ) {
        AnimatedContent(targetState = state, label = "AppContainer") {
            when (it) {
                SplashType.Splash -> SplashScreen()
                SplashType.Login -> ServiceSelectScreen()
                SplashType.Home -> HomeScreen()
            }
        }
    }
}
