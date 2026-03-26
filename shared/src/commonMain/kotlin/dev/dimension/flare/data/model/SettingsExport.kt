package dev.dimension.flare.data.model

import dev.dimension.flare.data.datastore.model.AppSettings
import kotlinx.serialization.Serializable

@Serializable
public data class SettingsExport(
    val appearanceSettings: AppearanceSettings,
    val appSettings: AppSettings,
    val tabSettings: TabSettings,
)
