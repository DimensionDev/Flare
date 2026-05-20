package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.model.appearance.GlobalAppearance
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class EnvironmentSettingsPresenter :
    PresenterBase<EnvironmentSettingsPresenter.State>(),
    KoinComponent {
    private val repository: AppDataStore by inject()

    @Composable
    override fun body(): State {
        val globalAppearance by repository.globalAppearance.collectAsUiState()
        val timelineAppearance by repository.timelineAppearance.collectAsUiState()
        val appSettings by repository.appSettings.collectAsUiState()
        return object : State {
            override val globalAppearance: UiState<GlobalAppearance> = globalAppearance
            override val timelineAppearance: UiState<TimelineAppearance> = timelineAppearance
            override val appSettings: UiState<AppSettings> = appSettings
        }
    }

    public interface State {
        public val globalAppearance: UiState<GlobalAppearance>
        public val timelineAppearance: UiState<TimelineAppearance>
        public val appSettings: UiState<AppSettings>
    }
}
