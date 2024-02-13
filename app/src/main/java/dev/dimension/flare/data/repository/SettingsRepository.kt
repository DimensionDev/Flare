package dev.dimension.flare.data.repository

import android.content.Context
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.TabSettings
import dev.dimension.flare.data.model.appearanceSettings
import dev.dimension.flare.data.model.tabSettings

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

    private val tabSettingsStore by lazy {
        context.tabSettings
    }

    val tabSettings by lazy {
        tabSettingsStore.data
    }

    suspend fun updateTabSettings(block: TabSettings.() -> TabSettings) {
        tabSettingsStore.updateData(block)
    }
}
