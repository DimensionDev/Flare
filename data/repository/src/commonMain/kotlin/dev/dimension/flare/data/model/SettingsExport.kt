package dev.dimension.flare.data.model

import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.model.appearance.AppearanceBag
import dev.dimension.flare.data.model.tab.TabSettingsV2
import kotlinx.serialization.Serializable

@Serializable
public data class SettingsExport(
    val appearanceBag: AppearanceBag,
    val appSettings: AppSettings,
    val tabSettingsV2: TabSettingsV2,
)

@Serializable
public data class LegacyAppearanceSettingsExport(
    val appearanceSettings: AppearanceSettings,
    val appSettings: AppSettings,
    val tabSettingsV2: TabSettingsV2,
)
