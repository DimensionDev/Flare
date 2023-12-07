package dev.dimension.flare.data.repository

import android.content.Context
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.appearanceSettings

class SettingsRepository(
    private val context: Context,
) {
    private val appearanceSettingsStore by lazy {
        context.appearanceSettings
    }
    val appearanceSettings by lazy {
        appearanceSettingsStore.data
    }

    suspend fun updateAppearanceSettings(block: AppearanceSettings.() -> AppearanceSettings) {
        appearanceSettingsStore.updateData(block)
    }
}
