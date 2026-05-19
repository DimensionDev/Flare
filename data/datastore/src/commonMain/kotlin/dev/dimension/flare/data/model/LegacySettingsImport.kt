package dev.dimension.flare.data.model

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.model.appearance.AppearanceBag
import dev.dimension.flare.data.model.appearance.LegacyAppearanceSettings
import dev.dimension.flare.data.model.appearance.toBag
import dev.dimension.flare.data.model.tab.TabSettingsV2
import dev.dimension.flare.data.model.tab.toTabSettingsV2
import kotlinx.serialization.Serializable

public data class SettingsImportData(
    val appearanceBag: AppearanceBag,
    val appSettings: AppSettings,
    val tabSettingsV2: TabSettingsV2,
)

public fun decodeLegacyAppearanceSettingsExport(jsonContent: String): SettingsImportData {
    val export = jsonContent.decodeJson(LegacyAppearanceSettingsExport.serializer())
    return SettingsImportData(
        appearanceBag = export.appearanceSettings.toBag(),
        appSettings = export.appSettings,
        tabSettingsV2 = export.tabSettingsV2,
    )
}

public fun decodeLegacySettingsExport(jsonContent: String): SettingsImportData {
    val export = jsonContent.decodeJson(LegacySettingsExport.serializer())
    return SettingsImportData(
        appearanceBag = export.appearanceBag,
        appSettings = export.appSettings,
        tabSettingsV2 = export.tabSettings.toTabSettingsV2(),
    )
}

public fun decodeLegacyAppearanceSettingsAndTabsExport(jsonContent: String): SettingsImportData {
    val export = jsonContent.decodeJson(LegacyAppearanceSettingsAndTabsExport.serializer())
    return SettingsImportData(
        appearanceBag = export.appearanceSettings.toBag(),
        appSettings = export.appSettings,
        tabSettingsV2 = export.tabSettings.toTabSettingsV2(),
    )
}

@Serializable
internal data class LegacyAppearanceSettingsExport(
    val appearanceSettings: LegacyAppearanceSettings,
    val appSettings: AppSettings,
    val tabSettingsV2: TabSettingsV2,
)

@Serializable
internal data class LegacySettingsExport(
    val appearanceBag: AppearanceBag,
    val appSettings: AppSettings,
    val tabSettings: TabSettings,
)

@Serializable
internal data class LegacyAppearanceSettingsAndTabsExport(
    val appearanceSettings: LegacyAppearanceSettings,
    val appSettings: AppSettings,
    val tabSettings: TabSettings,
)
