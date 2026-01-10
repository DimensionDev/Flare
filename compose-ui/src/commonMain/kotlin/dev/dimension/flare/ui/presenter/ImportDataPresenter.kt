package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.model.DataExport
import dev.dimension.flare.ui.presenter.settings.ImportAppDatabasePresenter
import org.koin.core.component.KoinComponent

public class ImportDataPresenter(
    private val jsonContent: String,
) : PresenterBase<ImportState>(),
    KoinComponent {
    @Composable
    override fun body(): ImportState =
        object : ImportState {
            override suspend fun import() {
                this@ImportDataPresenter.import()
            }
        }

    private suspend fun import() {
        val export = jsonContent.decodeJson(DataExport.serializer())

        val importAppDatabasePresenter = ImportAppDatabasePresenter(export.appDatabase.toString())
        importAppDatabasePresenter.import()

        val importSettingsPresenter = ImportSettingsPresenter(export.settings.toString())
        importSettingsPresenter.import()
    }
}
