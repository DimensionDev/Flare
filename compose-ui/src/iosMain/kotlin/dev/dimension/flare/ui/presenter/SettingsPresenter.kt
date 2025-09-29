package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.data.model.AppSettings
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.TabSettings
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class SettingsPresenter :
    PresenterBase<SettingsPresenter.State>(),
    KoinComponent {
    private val repository: SettingsRepository by inject()

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val appearanceSettings by repository.appearanceSettings.collectAsUiState()
        val appSettings by repository.appSettings.collectAsUiState()
        val tabSettings by repository.tabSettings.collectAsUiState()
        return object : State {
            override val appearance: UiState<AppearanceSettings> = appearanceSettings
            override val appSettings: UiState<AppSettings> = appSettings
            override val tabSettings: UiState<TabSettings> = tabSettings

            override fun updateAppearanceSettings(block: AppearanceSettings.() -> AppearanceSettings) {
                scope.launch {
                    repository.updateAppearanceSettings(block)
                }
            }

            override fun updateAppSettings(block: AppSettings.() -> AppSettings) {
                scope.launch {
                    repository.updateAppSettings(block)
                }
            }

            override fun updateTabSettings(block: TabSettings.() -> TabSettings) {
                scope.launch {
                    repository.updateTabSettings(block)
                }
            }
        }
    }

    public interface State {
        public val appearance: UiState<AppearanceSettings>
        public val appSettings: UiState<AppSettings>
        public val tabSettings: UiState<TabSettings>

        public fun updateAppearanceSettings(block: AppearanceSettings.() -> AppearanceSettings)

        public fun updateAppSettings(block: AppSettings.() -> AppSettings)

        public fun updateTabSettings(block: TabSettings.() -> TabSettings)
    }
}
