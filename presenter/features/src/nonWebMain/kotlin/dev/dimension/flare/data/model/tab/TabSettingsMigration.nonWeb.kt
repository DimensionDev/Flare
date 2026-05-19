package dev.dimension.flare.data.model.tab

import dev.dimension.flare.common.backgroundCoroutineContext
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.model.TabSettings
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okio.FileSystem
import okio.SYSTEM

internal actual suspend fun legacyTabSettingsExists(pathProducer: PlatformPathProducer): Boolean =
    withContext(backgroundCoroutineContext) {
        FileSystem.SYSTEM.exists(pathProducer.legacyTabSettingsPath())
    }

@OptIn(ExperimentalSerializationApi::class)
internal actual suspend fun readLegacyTabSettings(pathProducer: PlatformPathProducer): TabSettings? =
    withContext(backgroundCoroutineContext) {
        runCatching {
            FileSystem.SYSTEM.read(pathProducer.legacyTabSettingsPath()) {
                ProtoBuf.decodeFromByteArray<TabSettings>(readByteArray())
            }
        }.getOrNull()
    }

internal actual suspend fun deleteLegacyTabSettings(pathProducer: PlatformPathProducer): Unit =
    withContext(backgroundCoroutineContext) {
        runCatching {
            FileSystem.SYSTEM.delete(pathProducer.legacyTabSettingsPath())
        }
    }

private fun PlatformPathProducer.legacyTabSettingsPath() = dataStoreFile("tab_settings.pb")
