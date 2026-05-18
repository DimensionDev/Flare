package dev.dimension.flare.data.model.appearance

import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.model.AppearanceSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okio.FileSystem
import okio.SYSTEM

internal actual suspend fun legacyAppearanceSettingsExists(pathProducer: PlatformPathProducer): Boolean =
    withContext(Dispatchers.IO) {
        FileSystem.SYSTEM.exists(pathProducer.legacyAppearanceSettingsPath())
    }

@OptIn(ExperimentalSerializationApi::class)
internal actual suspend fun readLegacyAppearanceSettings(pathProducer: PlatformPathProducer): AppearanceSettings? =
    withContext(Dispatchers.IO) {
        runCatching {
            val v1Bytes =
                FileSystem.SYSTEM.read(pathProducer.legacyAppearanceSettingsPath()) {
                    readByteArray()
                }
            ProtoBuf.decodeFromByteArray<AppearanceSettings>(v1Bytes)
        }.getOrNull()
    }

internal actual suspend fun deleteLegacyAppearanceSettings(pathProducer: PlatformPathProducer): Unit =
    withContext(Dispatchers.IO) {
        runCatching {
            FileSystem.SYSTEM.delete(pathProducer.legacyAppearanceSettingsPath())
        }
    }

private fun PlatformPathProducer.legacyAppearanceSettingsPath() = dataStoreFile("appearance_settings.pb")
