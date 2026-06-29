package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.LegacyAppearanceSettingsAndTabsExport
import dev.dimension.flare.data.model.LegacyAppearanceSettingsExport
import dev.dimension.flare.data.model.LegacySettingsExport
import dev.dimension.flare.data.model.SettingsExport
import dev.dimension.flare.data.model.appearance.AppearanceBag
import dev.dimension.flare.data.model.tab.TabSettingsV2
import dev.dimension.flare.data.model.tab.toTabSettingsV2
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.di.koinInject
import kotlinx.serialization.json.jsonObject

public class ImportSettingsPresenter(
    private val jsonContent: String,
) : PresenterBase<ImportState>() {
    private val settingsRepository: SettingsRepository by koinInject()

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
                    ImportedSettings(
                        appearance = ImportedAppearance.Bag(export.appearanceBag),
                        appSettings = export.appSettings,
                        tabSettingsV2 = export.tabSettingsV2,
                    )
                }

                "tabSettingsV2" in root && "appearanceSettings" in root -> {
                    val export = jsonContent.decodeJson(LegacyAppearanceSettingsExport.serializer())
                    ImportedSettings(
                        appearance = ImportedAppearance.Settings(export.appearanceSettings),
                        appSettings = export.appSettings,
                        tabSettingsV2 = export.tabSettingsV2,
                    )
                }

                "tabSettings" in root && "appearanceBag" in root -> {
                    val export = jsonContent.decodeJson(LegacySettingsExport.serializer())
                    ImportedSettings(
                        appearance = ImportedAppearance.Bag(export.appearanceBag),
                        appSettings = export.appSettings,
                        tabSettingsV2 = export.tabSettings.toTabSettingsV2(),
                    )
                }

                "tabSettings" in root && "appearanceSettings" in root -> {
                    val export = jsonContent.decodeJson(LegacyAppearanceSettingsAndTabsExport.serializer())
                    ImportedSettings(
                        appearance = ImportedAppearance.Settings(export.appearanceSettings),
                        appSettings = export.appSettings,
                        tabSettingsV2 = export.tabSettings.toTabSettingsV2(),
                    )
                }

                else -> {
                    error("Unsupported settings export format")
                }
            }

        when (val appearance = imported.appearance) {
            is ImportedAppearance.Bag -> settingsRepository.replaceAppearance(appearance.value)
            is ImportedAppearance.Settings -> settingsRepository.replaceAppearance(appearance.value)
        }

        settingsRepository.updateAppSettings {
            imported.appSettings
        }

        settingsRepository.updateTabSettingsV2 {
            imported.tabSettingsV2
        }
    }

    private data class ImportedSettings(
        val appearance: ImportedAppearance,
        val appSettings: AppSettings,
        val tabSettingsV2: TabSettingsV2,
    )

    private sealed interface ImportedAppearance {
        data class Bag(
            val value: AppearanceBag,
        ) : ImportedAppearance

        data class Settings(
            val value: AppearanceSettings,
        ) : ImportedAppearance
    }
}
