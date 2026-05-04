package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class EnvironmentSettingsPresenter :
    PresenterBase<EnvironmentSettingsPresenter.State>(),
    KoinComponent {
    private val repository: SettingsRepository by inject()

    @Composable
    override fun body(): State {
        val appearanceSettings by repository.appearanceSettings.collectAsUiState()
        val appSettings by repository.appSettings.collectAsUiState()
        return object : State {
            override val appearanceSettings: UiState<AppearanceSettings> = appearanceSettings
            override val appSettings: UiState<AppSettings> = appSettings
        }
    }

    public interface State {
        public val appearanceSettings: UiState<AppearanceSettings>
        public val appSettings: UiState<AppSettings>
    }
}
