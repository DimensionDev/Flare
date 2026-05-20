package dev.dimension.flare.data.datastore

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.SettingsExport
import dev.dimension.flare.data.model.appearance.AppearanceBag
import dev.dimension.flare.data.model.tab.TabSettingsV2
import dev.dimension.flare.data.model.tab.TimelineSlot
import dev.dimension.flare.data.model.tab.TimelineSlotContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class AppDataStoreTest {
    @Test
    fun exposesSettingsReadWriteInterface() =
        runTest {
            withStore { store ->
                store.updateAppSettings {
                    copy(version = "settings-store")
                }
                store.replaceAppearanceBag(AppearanceBag(entries = mapOf("app.theme" to "dark")))
                store.updateTabSettingsV2 {
                    TabSettingsV2(homeSlots = listOf(manualGroupSlot()))
                }

                assertEquals(AppSettings(version = "settings-store"), store.appSettings.first())
                assertEquals(mapOf("app.theme" to "dark"), store.appearanceBag.first().entries)
                assertEquals(
                    listOf("manual-group"),
                    store.tabSettingsV2
                        .first()
                        .homeSlots
                        .map { it.id },
                )
            }
        }

    @Test
    fun importsSettingsExport() =
        runTest {
            withStore { store ->
                val export =
                    SettingsExport(
                        appearanceBag = AppearanceBag(entries = mapOf("app.theme" to "light")),
                        appSettings = AppSettings(version = "imported"),
                        tabSettingsV2 = TabSettingsV2(homeSlots = listOf(manualGroupSlot())),
                    ).encodeJson(SettingsExport.serializer())

                store.importSettings(export)

                assertEquals(mapOf("app.theme" to "light"), store.appearanceBag.first().entries)
                assertEquals(AppSettings(version = "imported"), store.appSettings.first())
                assertEquals(
                    listOf("manual-group"),
                    store.tabSettingsV2
                        .first()
                        .homeSlots
                        .map { it.id },
                )
            }
        }

    @Test
    fun exportsSettings() =
        runTest {
            withStore { store ->
                store.replaceAppearanceBag(AppearanceBag(entries = mapOf("app.theme" to "dark")))
                store.updateAppSettings {
                    copy(version = "exported")
                }
                store.updateTabSettingsV2 {
                    TabSettingsV2(homeSlots = listOf(manualGroupSlot()))
                }

                val export = store.exportSettings().decodeJson(SettingsExport.serializer())

                assertEquals(mapOf("app.theme" to "dark"), export.appearanceBag.entries)
                assertEquals(AppSettings(version = "exported"), export.appSettings)
                assertEquals(listOf("manual-group"), export.tabSettingsV2.homeSlots.map { it.id })
            }
        }

    private suspend fun withStore(block: suspend (AppDataStore) -> Unit) {
        val root = "/tmp/flare-settings-store-${Random.nextLong()}".toPath()
        val fs = FileSystem.SYSTEM
        fs.createDirectories(root)
        try {
            block(AppDataStore(OkioFileStorage(fs, root)))
        } finally {
            fs.deleteRecursively(root)
        }
    }

    private fun manualGroupSlot(): TimelineSlot =
        TimelineSlot(
            id = "manual-group",
            content = TimelineSlotContent.Group(),
        )
}
