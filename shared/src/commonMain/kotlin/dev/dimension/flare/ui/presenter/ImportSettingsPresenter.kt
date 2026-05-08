package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.LegacySettingsExport
import dev.dimension.flare.data.model.SettingsExport
import dev.dimension.flare.data.model.appearance.toPatch
import dev.dimension.flare.data.model.tab.TabSettingsV2
import dev.dimension.flare.data.model.tab.toTabSettingsV2
import dev.dimension.flare.data.repository.SettingsRepository
import kotlinx.serialization.json.jsonObject
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
        val root = JSON.parseToJsonElement(jsonContent).jsonObject
        val imported =
            when {
                "tabSettingsV2" in root -> {
                    val export = jsonContent.decodeJson(SettingsExport.serializer())
                    ImportedSettings(
                        appearanceSettings = export.appearanceSettings,
                        appSettings = export.appSettings,
                        tabSettingsV2 = export.tabSettingsV2,
                    )
                }
                "tabSettings" in root -> {
                    val export = jsonContent.decodeJson(LegacySettingsExport.serializer())
                    ImportedSettings(
                        appearanceSettings = export.appearanceSettings,
                        appSettings = export.appSettings,
                        tabSettingsV2 = export.tabSettings.toTabSettingsV2(),
                    )
                }
                else -> error("Unsupported settings export format")
            }

        settingsRepository.replaceAppearance(imported.appearanceSettings.toPatch())

        settingsRepository.updateAppSettings {
            imported.appSettings
        }

        settingsRepository.updateTabSettingsV2 {
            imported.tabSettingsV2
        }
    }

    private data class ImportedSettings(
        val appearanceSettings: AppearanceSettings,
        val appSettings: AppSettings,
        val tabSettingsV2: TabSettingsV2,
    )
}
