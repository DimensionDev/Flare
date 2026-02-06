package dev.dimension.flare.data.repository

import androidx.compose.runtime.Stable
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
import kotlinx.coroutines.flow.catch
import kotlinx.serialization.SerializationException
import okio.FileSystem
import okio.SYSTEM

@Stable
public class SettingsRepository internal constructor(
    private val pathProducer: PlatformPathProducer,
) {
    private val appearanceSettingsStore by lazy {
        createDataStore(
            name = "appearance_settings.pb",
            serializer = AccountPreferencesSerializer,
        )
    }

    // Defensive: if the serializer throws (corrupt/legacy bytes), emit default and don't crash the app.
    public val appearanceSettings: Flow<AppearanceSettings> by lazy {
        appearanceSettingsStore.data.catch { e ->
            if (e is SerializationException) {
                emit(AccountPreferencesSerializer.defaultValue)
            } else {
                throw e
            }
        }
    }
    private val appSettingsStore by lazy {
        createDataStore(
            name = "app_settings.pb",
            serializer = AppSettingsSerializer,
        )
    }
    public val appSettings: Flow<AppSettings> by lazy {
        appSettingsStore.data.catch { e ->
            if (e is SerializationException) {
                emit(AppSettingsSerializer.defaultValue)
            } else {
                throw e
            }
        }
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
        tabSettingsStore.data.catch { e ->
            if (e is SerializationException) {
                emit(TabSettingsSerializer.defaultValue)
            } else {
                throw e
            }
        }
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
