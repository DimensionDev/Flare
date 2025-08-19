package dev.dimension.flare.data.repository

import dev.dimension.flare.common.FileSystemUtilsExt
import dev.dimension.flare.data.model.AccountPreferencesSerializer
import dev.dimension.flare.data.model.AppSettings
import dev.dimension.flare.data.model.AppSettingsSerializer
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.TabSettings
import dev.dimension.flare.data.model.TabSettingsSerializer
import java.io.File
import kotlin.getValue

internal class SettingsRepository {
    private val appearanceSettingsStore by lazy {
        androidx.datastore.core.DataStoreFactory.create<AppearanceSettings>(
            serializer = AccountPreferencesSerializer,
            produceFile = { File(FileSystemUtilsExt.flareDirectory(), "appearance.pb") },
        )
    }
    val appearanceSettings by lazy {
        appearanceSettingsStore.data
    }
    private val appSettingsStore by lazy {
        androidx.datastore.core.DataStoreFactory.create<AppSettings>(
            serializer = AppSettingsSerializer,
            produceFile = { File(FileSystemUtilsExt.flareDirectory(), "app_settings.pb") },
        )
    }
    val appSettings by lazy {
        appSettingsStore.data
    }

    suspend fun updateAppearanceSettings(block: AppearanceSettings.() -> AppearanceSettings) {
        appearanceSettingsStore.updateData(block)
    }

    private val tabSettingsStore by lazy {
        androidx.datastore.core.DataStoreFactory.create<TabSettings>(
            serializer = TabSettingsSerializer,
            produceFile = { File(FileSystemUtilsExt.flareDirectory(), "tab_settings.pb") },
        )
    }

    val tabSettings by lazy {
        tabSettingsStore.data
    }

    suspend fun updateTabSettings(block: TabSettings.() -> TabSettings) {
        tabSettingsStore.updateData(block)
    }

    suspend fun updateAppSettings(block: AppSettings.() -> AppSettings) {
        appSettingsStore.updateData(block)
    }
}
