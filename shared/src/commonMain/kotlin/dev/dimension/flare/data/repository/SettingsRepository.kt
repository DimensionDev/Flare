package dev.dimension.flare.data.repository

import androidx.compose.runtime.Stable
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.TabSettings
import dev.dimension.flare.data.model.TabSettingsSerializer
import dev.dimension.flare.data.model.appearance.AppearanceBag
import dev.dimension.flare.data.model.appearance.AppearanceBagSerializer
import dev.dimension.flare.data.model.appearance.AppearanceKey
import dev.dimension.flare.data.model.appearance.AppearancePatch
import dev.dimension.flare.data.model.appearance.migrateAppearanceV1ToV2
import dev.dimension.flare.data.model.appearance.toAppearanceSettings
import dev.dimension.flare.data.model.appearance.toBag
import dev.dimension.flare.data.model.appearance.toPatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.SYSTEM

@Stable
public class SettingsRepository internal constructor(
    private val pathProducer: PlatformPathProducer,
    private val appDataStore: AppDataStore,
) {
    private val appearanceBagStore by lazy {
        createDataStore(
            name = "appearance_bag.pb",
            serializer = AppearanceBagSerializer,
        )
    }

    private val appearanceMigrationMutex = Mutex()
    private var appearanceMigrationCompleted = false

    public val appearancePatch: Flow<AppearancePatch> by lazy {
        flow {
            ensureAppearanceMigrated()
            emitAll(
                appearanceBagStore.data
                    .map { it.toPatch() }
                    .distinctUntilChanged(),
            )
        }
    }
    public val appearanceSettings: Flow<AppearanceSettings> by lazy {
        appearancePatch
            .map { it.toAppearanceSettings() }
            .distinctUntilChanged()
    }
    private val appSettingsStore: DataStore<AppSettings> by lazy { appDataStore.appSettingsStore }
    public val appSettings: Flow<AppSettings> by lazy {
        appSettingsStore.data
    }

    public suspend fun ensureAppearanceMigrated() {
        if (appearanceMigrationCompleted) return
        appearanceMigrationMutex.withLock {
            if (appearanceMigrationCompleted) return
            migrateAppearanceV1ToV2(pathProducer, appearanceBagStore)
            appearanceMigrationCompleted = true
        }
    }

    public suspend fun <T : Any> updateAppearance(
        key: AppearanceKey<T>,
        value: T,
    ) {
        updateAppearance { set(key, value) }
    }

    public suspend fun updateAppearance(block: AppearancePatch.() -> AppearancePatch) {
        ensureAppearanceMigrated()
        appearanceBagStore.updateData { bag ->
            bag.toPatch().block().toBag()
        }
    }

    public suspend fun clearAppearance(key: AppearanceKey<*>) {
        updateAppearance { clear(key) }
    }

    public suspend fun replaceAppearance(patch: AppearancePatch) {
        ensureAppearanceMigrated()
        appearanceBagStore.updateData { patch.toBag() }
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
