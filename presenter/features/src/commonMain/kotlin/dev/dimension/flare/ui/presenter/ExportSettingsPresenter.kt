package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import dev.dimension.flare.data.datastore.AppDataStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class ExportSettingsPresenter :
    PresenterBase<ExportState>(),
    KoinComponent {
    private val appDataStore: AppDataStore by inject()

    @Composable
    override fun body(): ExportState =
        object : ExportState {
            override suspend fun export(): String = this@ExportSettingsPresenter.export()
        }

    public suspend fun export(): String = appDataStore.exportSettings()
}
