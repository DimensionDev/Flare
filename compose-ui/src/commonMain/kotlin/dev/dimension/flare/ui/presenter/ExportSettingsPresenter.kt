package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.model.SettingsExport
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.model.UiState
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class ExportSettingsPresenter :
    PresenterBase<UiState<String>>(),
    KoinComponent {
    private val settingsRepository: SettingsRepository by inject()

    @Composable
    override fun body(): UiState<String> {
        var state by remember { mutableStateOf<UiState<String>>(UiState.Loading()) }

        LaunchedEffect(Unit) {
            try {
                state = UiState.Success(export())
            } catch (e: Exception) {
                state = UiState.Error(e)
            }
        }

        return state
    }

    public suspend fun export(): String {
        val export =
            SettingsExport(
                appearanceSettings = settingsRepository.appearanceSettings.first(),
                appSettings = settingsRepository.appSettings.first(),
                tabSettings = settingsRepository.tabSettings.first(),
            )
        return export.encodeJson(SettingsExport.serializer())
    }
}
