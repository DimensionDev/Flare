package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.model.DataExport
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.settings.ImportAppDatabasePresenter
import org.koin.core.component.KoinComponent

public class ImportDataPresenter(
    private val jsonContent: String,
) : PresenterBase<UiState<Unit>>(),
    KoinComponent {
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

    private suspend fun import() {
        val export = jsonContent.decodeJson(DataExport.serializer())

        val importAppDatabasePresenter = ImportAppDatabasePresenter(export.appDatabase.toString())
        importAppDatabasePresenter.import()

        val importSettingsPresenter = ImportSettingsPresenter(export.settings.toString())
        importSettingsPresenter.import()
    }
}
