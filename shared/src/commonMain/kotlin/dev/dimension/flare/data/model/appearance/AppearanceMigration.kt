package dev.dimension.flare.data.model.appearance

import androidx.datastore.core.DataStore
import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.io.FileStorage
import dev.dimension.flare.data.model.AppearanceSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf

@OptIn(ExperimentalSerializationApi::class)
internal suspend fun migrateAppearanceV1ToV2(
    fileStorage: FileStorage,
    bagStore: DataStore<AppearanceBag>,
) {
    withContext(PlatformDispatchers.IO) {
        val v1Path = fileStorage.dataStoreFile("appearance_settings.pb")
        if (!fileStorage.exists(v1Path)) return@withContext
        if (bagStore.data
                .first()
                .entries
                .isNotEmpty()
        ) {
            runCatching { fileStorage.delete(v1Path) }
            return@withContext
        }

        val v1 =
            runCatching {
                val v1Bytes = fileStorage.read(v1Path)
                ProtoBuf.decodeFromByteArray<AppearanceSettings>(v1Bytes)
            }.getOrNull()
        if (v1 != null) {
            bagStore.updateData { v1.toPatch().toBag() }
        }
        runCatching { fileStorage.delete(v1Path) }
    }
}
