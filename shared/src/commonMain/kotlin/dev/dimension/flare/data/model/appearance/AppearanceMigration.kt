package dev.dimension.flare.data.model.appearance

import androidx.datastore.core.DataStore
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.model.AppearanceSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okio.FileSystem
import okio.SYSTEM

@OptIn(ExperimentalSerializationApi::class)
internal suspend fun migrateAppearanceV1ToV2(
    pathProducer: PlatformPathProducer,
    bagStore: DataStore<AppearanceBag>,
) {
    withContext(Dispatchers.IO) {
        val v1Path = pathProducer.dataStoreFile("appearance_settings.pb")
        val fs = FileSystem.SYSTEM
        if (!fs.exists(v1Path)) return@withContext
        if (bagStore.data
                .first()
                .entries
                .isNotEmpty()
        ) {
            runCatching { fs.delete(v1Path) }
            return@withContext
        }

        val v1 =
            runCatching {
                val v1Bytes = fs.read(v1Path) { readByteArray() }
                ProtoBuf.decodeFromByteArray<AppearanceSettings>(v1Bytes)
            }.getOrNull()
        if (v1 != null) {
            bagStore.updateData { v1.toPatch().toBag() }
        }
        runCatching { fs.delete(v1Path) }
    }
}
