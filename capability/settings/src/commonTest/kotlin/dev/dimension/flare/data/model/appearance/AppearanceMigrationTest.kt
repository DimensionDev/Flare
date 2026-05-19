package dev.dimension.flare.data.model.appearance

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import dev.dimension.flare.common.protobufSerializer
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.io.PlatformPathProducer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppearanceMigrationTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun v1FileMigratesToV2BagAndDeletesOldFile() =
        runTest {
            val root = "/tmp/flare-appearance-${Random.nextLong()}".toPath()
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
            val oldPath = pathProducer.dataStoreFile("appearance_settings.pb")
            val imported =
                LegacyAppearanceSettings(
                    theme = LegacyTheme.DARK,
                    showMedia = false,
                    videoAutoplay = LegacyVideoAutoplay.ALWAYS,
                    postActionStyle = LegacyPostActionStyle.Stretch,
                )
            fs.write(oldPath) {
                write(ProtoBuf.encodeToByteArray(imported))
            }
            val store =
                DataStoreFactory.create(
                    storage =
                        OkioStorage(
                            fileSystem = fs,
                            serializer = protobufSerializer(AppearanceBag()),
                            producePath = { pathProducer.dataStoreFile("appearance_bag.pb") },
                        ),
                )

            migrateAppearanceV1ToV2(
                fileStorage = OkioFileStorage(fs),
                legacyAppearanceSettingsPath = oldPath,
                bagStore = store,
            )

            assertEquals(
                imported.toBag(),
                store.data
                    .first(),
            )
            assertFalse(fs.exists(oldPath))
            fs.deleteRecursively(root)
        }
}
