package dev.dimension.flare.data.model.appearance

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import dev.dimension.flare.common.protobufSerializer
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.PostActionStyle
import dev.dimension.flare.data.model.Theme
import dev.dimension.flare.data.model.VideoAutoplay
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
                AppearanceSettings(
                    theme = Theme.DARK,
                    showMedia = false,
                    videoAutoplay = VideoAutoplay.ALWAYS,
                    postActionStyle = PostActionStyle.Stretch,
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

            migrateAppearanceV1ToV2(pathProducer, store)

            assertEquals(
                imported.toPatch().toBag(),
                store.data
                    .first(),
            )
            assertFalse(fs.exists(oldPath))
            fs.deleteRecursively(root)
        }
}
