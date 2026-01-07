package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.model.SettingsExport
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.model.UiState
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class ImportSettingsPresenter(
    private val jsonContent: String,
) : PresenterBase<UiState<Unit>>(),
    KoinComponent {
    private val settingsRepository: SettingsRepository by inject()

    @Composable
    override fun body(): UiState<Unit> {
        var state by remember { mutableStateOf<UiState<Unit>>(UiState.Loading()) }

        LaunchedEffect(Unit) {
            try {
                import()
                state = UiState.Success(Unit)
            } catch (e: Exception) {
                state = UiState.Error(e)
            }
        }

        return state
    }

    public suspend fun import() {
        val export = jsonContent.decodeJson(SettingsExport.serializer())

        settingsRepository.updateAppearanceSettings {
            export.appearanceSettings
        }

        settingsRepository.updateAppSettings {
            export.appSettings
        }

        settingsRepository.updateTabSettings {
            export.tabSettings
        }
    }
}
