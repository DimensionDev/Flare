package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.model.DataExport
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.settings.ExportAppDatabasePresenter
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent

public class ExportDataPresenter :
    PresenterBase<UiState<String>>(),
    KoinComponent {
    private val exportAppDatabasePresenter = ExportAppDatabasePresenter()
    private val exportSettingsPresenter = ExportSettingsPresenter()
    private val json = Json { ignoreUnknownKeys = true }

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
        val appDatabaseJson = exportAppDatabasePresenter.export()
        val settingsJson = exportSettingsPresenter.export()

        val export =
            DataExport(
                appDatabase = json.parseToJsonElement(appDatabaseJson),
                settings = json.parseToJsonElement(settingsJson),
            )
        return export.encodeJson(DataExport.serializer())
    }
}
