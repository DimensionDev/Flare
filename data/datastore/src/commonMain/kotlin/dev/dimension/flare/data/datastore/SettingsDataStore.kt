package dev.dimension.flare.data.datastore

import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.model.appearance.AppearanceBag
import dev.dimension.flare.data.model.appearance.migrateAppearanceV1ToV2
import dev.dimension.flare.data.model.tab.TabSettingsV2
import dev.dimension.flare.data.model.tab.migrateTabSettingsV1ToV2
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public class SettingsDataStore(
    private val pathProducer: PlatformPathProducer,
    private val appDataStore: AppDataStore,
) {
    private val appearanceBagStore by lazy {
        appDataStore.appearanceBagStore
    }
    private val appSettingsStore by lazy {
        appDataStore.appSettingsStore
    }
    private val tabSettingsV2Store by lazy {
        appDataStore.tabSettingsV2Store
    }

    private val appearanceMigrationMutex = Mutex()
    private var appearanceMigrationCompleted = false
    private val tabSettingsMigrationMutex = Mutex()
    private var tabSettingsMigrationCompleted = false

    public val appearanceBag: Flow<AppearanceBag> by lazy {
        flow {
            ensureAppearanceMigrated()
            emitAll(
                appearanceBagStore.data
                    .distinctUntilChanged(),
            )
        }
    }

    public val appSettings: Flow<AppSettings> by lazy {
        appSettingsStore.data
    }

    public val tabSettingsV2: Flow<TabSettingsV2> by lazy {
        flow {
            ensureTabSettingsMigrated()
            emitAll(tabSettingsV2Store.data)
        }
    }

    public suspend fun ensureAppearanceMigrated() {
        if (appearanceMigrationCompleted) return
        appearanceMigrationMutex.withLock {
            if (appearanceMigrationCompleted) return
            migrateAppearanceV1ToV2(pathProducer, appearanceBagStore)
            appearanceMigrationCompleted = true
        }
    }

    public suspend fun updateAppearanceBag(block: AppearanceBag.() -> AppearanceBag) {
        ensureAppearanceMigrated()
        appearanceBagStore.updateData(block)
    }

    public suspend fun replaceAppearanceBag(bag: AppearanceBag) {
        updateAppearanceBag { bag }
    }

    public suspend fun ensureTabSettingsMigrated() {
        if (tabSettingsMigrationCompleted) return
        tabSettingsMigrationMutex.withLock {
            if (tabSettingsMigrationCompleted) return
            migrateTabSettingsV1ToV2(
                pathProducer = pathProducer,
                tabSettingsV2Store = tabSettingsV2Store,
            )
            tabSettingsMigrationCompleted = true
        }
    }

    public suspend fun updateTabSettingsV2(block: TabSettingsV2.() -> TabSettingsV2) {
        ensureTabSettingsMigrated()
        tabSettingsV2Store.updateData(block)
    }

    public suspend fun updateAppSettings(block: AppSettings.() -> AppSettings) {
        appSettingsStore.updateData(block)
    }
}
