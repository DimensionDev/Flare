package dev.dimension.flare.data.model

import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.model.tab.TabSettingsV2
import kotlinx.serialization.Serializable

@Serializable
internal data class SettingsExport(
    val appearanceSettings: AppearanceSettings,
    val appSettings: AppSettings,
    val tabSettingsV2: TabSettingsV2,
)

@Serializable
internal data class LegacySettingsExport(
    val appearanceSettings: AppearanceSettings,
    val appSettings: AppSettings,
    val tabSettings: TabSettings,
)
