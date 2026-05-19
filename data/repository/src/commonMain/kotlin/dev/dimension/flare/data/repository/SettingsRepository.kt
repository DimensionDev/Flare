package dev.dimension.flare.data.repository

import androidx.compose.runtime.Stable
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import dev.dimension.flare.common.protobufSerializer
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.createDataStoreStorage
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.appearance.AppearanceBag
import dev.dimension.flare.data.model.appearance.AppearanceKey
import dev.dimension.flare.data.model.appearance.AppearancePatch
import dev.dimension.flare.data.model.appearance.GlobalAppearance
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.data.model.appearance.migrateAppearanceV1ToV2
import dev.dimension.flare.data.model.appearance.toBag
import dev.dimension.flare.data.model.appearance.toGlobalAppearance
import dev.dimension.flare.data.model.appearance.toPatch
import dev.dimension.flare.data.model.appearance.toTimelineAppearance
import dev.dimension.flare.data.model.tab.TabSettingsV2
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Stable
public class SettingsRepository(
    private val pathProducer: PlatformPathProducer,
    private val appDataStore: AppDataStore,
    private val tabSettingsV2Migrator: TabSettingsV2Migrator = NoOpTabSettingsV2Migrator,
) {
    private val appearanceBagStore by lazy {
        createDataStore(
            name = "appearance_bag.pb",
            serializer = protobufSerializer(AppearanceBag()),
        )
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

    public val appearancePatch: Flow<AppearancePatch> by lazy {
        appearanceBag
            .map { it.toPatch() }
            .distinctUntilChanged()
    }
    public val globalAppearance: Flow<GlobalAppearance> by lazy {
        appearancePatch
            .map { it.toGlobalAppearance() }
            .distinctUntilChanged()
    }
    public val timelineAppearance: Flow<TimelineAppearance> by lazy {
        appearancePatch
            .map { it.toTimelineAppearance() }
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

    public suspend fun replaceAppearance(bag: AppearanceBag) {
        ensureAppearanceMigrated()
        appearanceBagStore.updateData { bag }
    }

    public suspend fun replaceAppearance(settings: AppearanceSettings) {
        ensureAppearanceMigrated()
        appearanceBagStore.updateData { settings.toPatch().toBag() }
    }

//    private val tabSettingsStore by lazy {
//        createDataStore(
//            name = "tab_settings.pb",
//            serializer = protobufSerializer(TabSettings()),
//        )
//    }

//    public val tabSettings: Flow<TabSettings> by lazy {
//        tabSettingsStore.data
//    }
//
//    public suspend fun updateTabSettings(block: TabSettings.() -> TabSettings) {
//        tabSettingsStore.updateData(block)
//    }

    private val tabSettingsV2Store by lazy {
        createDataStore(
            name = "tab_settings_v2.pb",
            serializer = protobufSerializer(TabSettingsV2()),
        )
    }

    public val tabSettingsV2: Flow<TabSettingsV2> by lazy {
        flow {
            ensureTabSettingsMigrated()
            emitAll(tabSettingsV2Store.data)
        }
    }

    public suspend fun updateTabSettingsV2(block: TabSettingsV2.() -> TabSettingsV2) {
        ensureTabSettingsMigrated()
        tabSettingsV2Store.updateData(block)
    }

    public suspend fun ensureTabSettingsMigrated() {
        if (tabSettingsMigrationCompleted) return
        tabSettingsMigrationMutex.withLock {
            if (tabSettingsMigrationCompleted) return
            tabSettingsV2Migrator.migrate(
                pathProducer = pathProducer,
                tabSettingsV2Store = tabSettingsV2Store,
            )
            tabSettingsMigrationCompleted = true
        }
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
                createDataStoreStorage(
                    name = name,
                    serializer = serializer,
                    platformPathProducer = pathProducer,
                ),
        )
}
