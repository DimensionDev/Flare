package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.model.SettingsExport
import dev.dimension.flare.data.repository.SettingsRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class ImportSettingsPresenter(
    private val jsonContent: String,
) : PresenterBase<ImportState>(),
    KoinComponent {
    private val settingsRepository: SettingsRepository by inject()

    @Composable
    override fun body(): ImportState =
        object : ImportState {
            override suspend fun import() {
                this@ImportSettingsPresenter.import()
            }
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
