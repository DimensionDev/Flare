package dev.dimension.flare.data.repository

import androidx.compose.runtime.Stable
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.model.appearance.AppearanceBag
import dev.dimension.flare.data.model.appearance.AppearanceKey
import dev.dimension.flare.data.model.appearance.AppearancePatch
import dev.dimension.flare.data.model.appearance.GlobalAppearance
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.data.model.appearance.toBag
import dev.dimension.flare.data.model.appearance.toGlobalAppearance
import dev.dimension.flare.data.model.appearance.toPatch
import dev.dimension.flare.data.model.appearance.toTimelineAppearance
import dev.dimension.flare.data.model.tab.TabSettingsV2
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Stable
public class SettingsRepository(
    private val appDataStore: AppDataStore,
) {
    public val appearanceBag: Flow<AppearanceBag> by lazy {
        appDataStore.appearanceBag
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
        appDataStore.appSettings
    }

    public suspend fun ensureAppearanceMigrated() {
        appDataStore.ensureAppearanceMigrated()
    }

    public suspend fun <T : Any> updateAppearance(
        key: AppearanceKey<T>,
        value: T,
    ) {
        updateAppearance { set(key, value) }
    }

    public suspend fun updateAppearance(block: AppearancePatch.() -> AppearancePatch) {
        appDataStore.updateAppearanceBag {
            toPatch().block().toBag()
        }
    }

    public suspend fun clearAppearance(key: AppearanceKey<*>) {
        updateAppearance { clear(key) }
    }

    public suspend fun replaceAppearance(patch: AppearancePatch) {
        appDataStore.replaceAppearanceBag(patch.toBag())
    }

    public suspend fun replaceAppearance(bag: AppearanceBag) {
        appDataStore.replaceAppearanceBag(bag)
    }

    public val tabSettingsV2: Flow<TabSettingsV2> by lazy {
        appDataStore.tabSettingsV2
    }

    public suspend fun updateTabSettingsV2(block: TabSettingsV2.() -> TabSettingsV2) {
        appDataStore.updateTabSettingsV2(block)
    }

    public suspend fun ensureTabSettingsMigrated() {
        appDataStore.ensureTabSettingsMigrated()
    }

    public suspend fun updateAppSettings(block: AppSettings.() -> AppSettings) {
        appDataStore.updateAppSettings(block)
    }
}
