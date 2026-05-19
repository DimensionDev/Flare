package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.model.SettingsExport
import dev.dimension.flare.data.model.SettingsImportData
import dev.dimension.flare.data.model.decodeLegacyAppearanceSettingsAndTabsExport
import dev.dimension.flare.data.model.decodeLegacyAppearanceSettingsExport
import dev.dimension.flare.data.model.decodeLegacySettingsExport
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
                "tabSettingsV2" in root && "appearanceBag" in root -> {
                    val export = jsonContent.decodeJson(SettingsExport.serializer())
                    SettingsImportData(
                        appearanceBag = export.appearanceBag,
                        appSettings = export.appSettings,
                        tabSettingsV2 = export.tabSettingsV2,
                    )
                }

                "tabSettingsV2" in root && "appearanceSettings" in root -> {
                    decodeLegacyAppearanceSettingsExport(jsonContent)
                }

                "tabSettings" in root && "appearanceBag" in root -> {
                    decodeLegacySettingsExport(jsonContent)
                }

                "tabSettings" in root && "appearanceSettings" in root -> {
                    decodeLegacyAppearanceSettingsAndTabsExport(jsonContent)
                }

                else -> {
                    error("Unsupported settings export format")
                }
            }

        settingsRepository.replaceAppearance(imported.appearanceBag)

        settingsRepository.updateAppSettings {
            imported.appSettings
        }

        settingsRepository.updateTabSettingsV2 {
            imported.tabSettingsV2
        }
    }
}
