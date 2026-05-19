package dev.dimension.flare.data.datastore

import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.model.appearance.AppearanceBag
import dev.dimension.flare.data.model.tab.TabSettingsV2
import dev.dimension.flare.data.model.tab.TimelineSlot
import dev.dimension.flare.data.model.tab.TimelineSlotContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsDataStoreTest {
    @Test
    fun exposesSettingsReadWriteInterface() =
        runTest {
            val root = "/tmp/flare-settings-store-${Random.nextLong()}".toPath()
            val fs = FileSystem.SYSTEM
            fs.createDirectories(root)
            val pathProducer =
                object : PlatformPathProducer {
                    override fun dataStoreFile(fileName: String): Path = root.resolve(fileName)

                    override fun draftMediaFile(
                        groupId: String,
                        fileName: String,
                    ): Path = root.resolve(groupId).resolve(fileName)
                }
            val store =
                SettingsDataStore(
                    pathProducer = pathProducer,
                    appDataStore = AppDataStore(pathProducer),
                )

            store.updateAppSettings {
                copy(version = "settings-store")
            }
            store.replaceAppearanceBag(AppearanceBag(entries = mapOf("app.theme" to "dark")))
            store.updateTabSettingsV2 {
                TabSettingsV2(
                    homeSlots =
                        listOf(
                            TimelineSlot(
                                id = "manual-group",
                                content = TimelineSlotContent.Group(),
                            ),
                        ),
                )
            }

            assertEquals(AppSettings(version = "settings-store"), store.appSettings.first())
            assertEquals(mapOf("app.theme" to "dark"), store.appearanceBag.first().entries)
            assertEquals(listOf("manual-group"), store.tabSettingsV2.first().homeSlots.map { it.id })

            fs.deleteRecursively(root)
        }
}
