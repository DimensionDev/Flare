package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.model.SettingsExport
import dev.dimension.flare.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class ExportSettingsPresenter :
    PresenterBase<ExportState>(),
    KoinComponent {
    private val settingsRepository: SettingsRepository by inject()

    @Composable
    override fun body(): ExportState =
        object : ExportState {
            override suspend fun export(): String = this@ExportSettingsPresenter.export()
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
