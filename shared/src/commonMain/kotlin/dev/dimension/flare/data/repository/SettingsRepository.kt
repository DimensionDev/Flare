package dev.dimension.flare.data.repository

import androidx.compose.runtime.Stable
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import dev.dimension.flare.common.protobufSerializer
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.appearance.AppearanceBag
import dev.dimension.flare.data.model.appearance.AppearanceKey
import dev.dimension.flare.data.model.appearance.AppearancePatch
import dev.dimension.flare.data.model.appearance.migrateAppearanceV1ToV2
import dev.dimension.flare.data.model.appearance.toAppearanceSettings
import dev.dimension.flare.data.model.appearance.toBag
import dev.dimension.flare.data.model.appearance.toPatch
import dev.dimension.flare.data.model.tab.TabSettingsV2
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.model.tab.isSystemHomeMixedTimeline
import dev.dimension.flare.data.model.tab.migrateTabSettingsV1ToV2
import dev.dimension.flare.data.model.tab.withSystemHomeMixedTimelineEnabled
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
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
    private val timelineResolver: TimelineResolver,
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

    internal val tabSettingsV2: Flow<TabSettingsV2> by lazy {
        flow {
            ensureTabSettingsMigrated()
            emitAll(tabSettingsV2Store.data)
        }
    }

    public val homeTimelineTabs: Flow<List<TimelineTabItemV2>> by lazy {
        tabSettingsV2
            .distinctUntilChangedBy { it.homeSlots }
            .map { settings ->
                val tabs =
                    settings.homeSlots
                    .map { timelineResolver.toTabItem(it) }
                    .filter { it.enabled }
                tabs.withSystemHomeMixedTimelineEnabled(
                    enabled = tabs.any { it.isSystemHomeMixedTimeline },
                )
            }
    }

    internal suspend fun updateTabSettingsV2(block: TabSettingsV2.() -> TabSettingsV2) {
        ensureTabSettingsMigrated()
        tabSettingsV2Store.updateData(block)
    }

    internal suspend fun replaceHomeTimelineTabs(tabs: List<TimelineTabItemV2>) {
        updateTabSettingsV2 {
            val normalizedTabs =
                tabs.withSystemHomeMixedTimelineEnabled(
                    enabled = tabs.any { it.isSystemHomeMixedTimeline },
                )
            copy(
                homeSlots =
                    normalizedTabs
                        .distinctBy { it.id }
                        .map { timelineResolver.toSlot(it) },
            )
        }
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
