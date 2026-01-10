package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.model.DataExport
import dev.dimension.flare.ui.presenter.settings.ExportAppDatabasePresenter
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent

public class ExportDataPresenter :
    PresenterBase<ExportState>(),
    KoinComponent {
    private val exportAppDatabasePresenter = ExportAppDatabasePresenter()
    private val exportSettingsPresenter = ExportSettingsPresenter()
    private val json = Json { ignoreUnknownKeys = true }

    @Composable
    override fun body(): ExportState =
        object : ExportState {
            override suspend fun export(): String = this@ExportDataPresenter.export()
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
