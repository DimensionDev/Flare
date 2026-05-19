package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import dev.dimension.flare.data.datastore.AppDataStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class ImportSettingsPresenter(
    private val jsonContent: String,
) : PresenterBase<ImportState>(),
    KoinComponent {
    private val appDataStore: AppDataStore by inject()

    @Composable
    override fun body(): ImportState =
        object : ImportState {
            override suspend fun import() {
                this@ImportSettingsPresenter.import()
            }
        }

    public suspend fun import() {
        appDataStore.importSettings(jsonContent)
    }
}
