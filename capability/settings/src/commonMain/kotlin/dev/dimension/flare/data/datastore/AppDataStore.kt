package dev.dimension.flare.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.common.protobufSerializer
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.ComposeConfigData
import dev.dimension.flare.data.datastore.model.FlareConfig
import dev.dimension.flare.data.io.FileStorage
import dev.dimension.flare.data.model.SettingsExport
import dev.dimension.flare.data.model.SettingsImportData
import dev.dimension.flare.data.model.decodeLegacyAppearanceSettingsAndTabsExport
import dev.dimension.flare.data.model.decodeLegacyAppearanceSettingsExport
import dev.dimension.flare.data.model.decodeLegacySettingsExport
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
import dev.dimension.flare.data.model.tab.migrateTabSettingsV1ToV2
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.jsonObject

public class AppDataStore(
    private val fileStorage: FileStorage,
) {
    private val appearanceMigrationMutex = Mutex()
    private var appearanceMigrationCompleted = false
    private val tabSettingsMigrationMutex = Mutex()
    private var tabSettingsMigrationCompleted = false

    public val flareDataStore: DataStore<FlareConfig> by lazy {
        createDataStore(
            name = "flare_config.pb",
            defaultValue = FlareConfig(),
        )
    }

    public val composeConfigData: DataStore<ComposeConfigData> by lazy {
        createDataStore(
            name = "compose_config.pb",
            defaultValue = ComposeConfigData(),
        )
    }

    private val appSettingsStore: DataStore<AppSettings> by lazy {
        createDataStore(
            name = "app_settings.pb",
            defaultValue = AppSettings(version = ""),
        )
    }

    private val appearanceBagStore: DataStore<AppearanceBag> by lazy {
        createDataStore(
            name = "appearance_bag.pb",
            defaultValue = AppearanceBag(),
        )
    }

    private val tabSettingsV2Store: DataStore<TabSettingsV2> by lazy {
        createDataStore(
            name = "tab_settings_v2.pb",
            defaultValue = TabSettingsV2(),
        )
    }

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
            migrateAppearanceV1ToV2(
                fileStorage = fileStorage,
                legacyAppearanceSettingsPath =
                    fileStorage.dataStoreFile(LEGACY_APPEARANCE_SETTINGS_FILE_NAME),
                bagStore = appearanceBagStore,
            )
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

    public suspend fun <T : Any> updateAppearance(
        key: AppearanceKey<T>,
        value: T,
    ) {
        updateAppearance { set(key, value) }
    }

    public suspend fun updateAppearance(block: AppearancePatch.() -> AppearancePatch) {
        updateAppearanceBag {
            toPatch().block().toBag()
        }
    }

    public suspend fun clearAppearance(key: AppearanceKey<*>) {
        updateAppearance { clear(key) }
    }

    public suspend fun replaceAppearance(patch: AppearancePatch) {
        replaceAppearanceBag(patch.toBag())
    }

    public suspend fun replaceAppearance(bag: AppearanceBag) {
        replaceAppearanceBag(bag)
    }

    public suspend fun ensureTabSettingsMigrated() {
        if (tabSettingsMigrationCompleted) return
        tabSettingsMigrationMutex.withLock {
            if (tabSettingsMigrationCompleted) return
            migrateTabSettingsV1ToV2(
                fileStorage = fileStorage,
                legacyTabSettingsPath = fileStorage.dataStoreFile(LEGACY_TAB_SETTINGS_FILE_NAME),
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

    public suspend fun importSettings(jsonContent: String) {
        val imported = decodeSettingsImport(jsonContent)
        replaceAppearanceBag(imported.appearanceBag)
        updateAppSettings { imported.appSettings }
        updateTabSettingsV2 { imported.tabSettingsV2 }
    }

    public suspend fun exportSettings(): String {
        ensureAppearanceMigrated()
        ensureTabSettingsMigrated()
        val export =
            SettingsExport(
                appearanceBag = appearanceBagStore.data.first(),
                appSettings = appSettingsStore.data.first(),
                tabSettingsV2 = tabSettingsV2Store.data.first(),
            )
        return export.encodeJson(SettingsExport.serializer())
    }

    private fun decodeSettingsImport(jsonContent: String): SettingsImportData {
        val root = JSON.parseToJsonElement(jsonContent).jsonObject
        return when {
            "tabSettingsV2" in root && "appearanceBag" in root -> {
                val export = jsonContent.decodeJson(SettingsExport.serializer())
                SettingsImportData(
                    appearanceBag = export.appearanceBag,
                    appSettings = export.appSettings,
                    tabSettingsV2 = export.tabSettingsV2,
                )
            }

            "tabSettingsV2" in root && "appearanceSettings" in root -> {
                decodeLegacyAppearanceSettingsExport(jsonContent)
            }

            "tabSettings" in root && "appearanceBag" in root -> {
                decodeLegacySettingsExport(jsonContent)
            }

            "tabSettings" in root && "appearanceSettings" in root -> {
                decodeLegacyAppearanceSettingsAndTabsExport(jsonContent)
            }

            else -> {
                error("Unsupported settings export format")
            }
        }
    }

    private inline fun <reified T> createDataStore(
        name: String,
        defaultValue: T,
    ): DataStore<T> =
        DataStoreFactory.create(
            storage =
                createDataStoreStorage(
                    name = name,
                    serializer = protobufSerializer(defaultValue),
                    fileStorage = fileStorage,
                ),
        )
}

private const val LEGACY_APPEARANCE_SETTINGS_FILE_NAME = "appearance_settings.pb"
private const val LEGACY_TAB_SETTINGS_FILE_NAME = "tab_settings.pb"
