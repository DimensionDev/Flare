package dev.dimension.flare.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.model.AccountPreferencesSerializer
import dev.dimension.flare.data.model.AppSettings
import dev.dimension.flare.data.model.AppSettingsSerializer
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.TabSettings
import dev.dimension.flare.data.model.TabSettingsSerializer
import kotlinx.coroutines.flow.Flow
import okio.FileSystem
import okio.SYSTEM

public class SettingsRepository internal constructor(
    private val pathProducer: PlatformPathProducer,
) {
    private val appearanceSettingsStore by lazy {
        createDataStore(
            name = "appearance_settings.pb",
            serializer = AccountPreferencesSerializer,
        )
    }
    public val appearanceSettings: Flow<AppearanceSettings> by lazy {
        appearanceSettingsStore.data
    }
    private val appSettingsStore by lazy {
        createDataStore(
            name = "app_settings.pb",
            serializer = AppSettingsSerializer,
        )
    }
    public val appSettings: Flow<AppSettings> by lazy {
        appSettingsStore.data
    }

    public suspend fun updateAppearanceSettings(block: AppearanceSettings.() -> AppearanceSettings) {
        appearanceSettingsStore.updateData(block)
    }

    private val tabSettingsStore by lazy {
        createDataStore(
            name = "tab_settings.pb",
            serializer = TabSettingsSerializer,
        )
    }

    public val tabSettings: Flow<TabSettings> by lazy {
        tabSettingsStore.data
    }

    public suspend fun updateTabSettings(block: TabSettings.() -> TabSettings) {
        tabSettingsStore.updateData(block)
    }

    public suspend fun updateAppSettings(block: AppSettings.() -> AppSettings) {
        appSettingsStore.updateData(block)
    }

    private inline fun <reified T> createDataStore(
        name: String,
        serializer: androidx.datastore.core.okio.OkioSerializer<T>,
    ): DataStore<T> =
        DataStoreFactory.create(
            storage =
                OkioStorage(
                    fileSystem = FileSystem.SYSTEM,
                    serializer = serializer,
                    producePath = {
                        pathProducer.dataStoreFile(name)
                    },
                ),
        )
}
