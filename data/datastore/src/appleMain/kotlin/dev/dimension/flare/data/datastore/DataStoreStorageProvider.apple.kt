package dev.dimension.flare.data.datastore

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioStorage
import dev.dimension.flare.common.protobufSerializer
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.ComposeConfigData
import dev.dimension.flare.data.datastore.model.FlareConfig
import dev.dimension.flare.data.io.PlatformPathProducer
import okio.FileSystem
import okio.SYSTEM

internal actual class DataStoreStorageProvider actual constructor(
    private val platformPathProducer: PlatformPathProducer,
) {
    actual fun flareConfigStorage(): Storage<FlareConfig> =
        OkioStorage(
            fileSystem = FileSystem.SYSTEM,
            serializer = protobufSerializer(FlareConfig()),
            producePath = {
                platformPathProducer.dataStoreFile("flare_config.pb")
            },
        )

    actual fun composeConfigStorage(): Storage<ComposeConfigData> =
        OkioStorage(
            fileSystem = FileSystem.SYSTEM,
            serializer = protobufSerializer(ComposeConfigData()),
            producePath = {
                platformPathProducer.dataStoreFile("compose_config.pb")
            },
        )

    actual fun appSettingsStorage(): Storage<AppSettings> =
        OkioStorage(
            fileSystem = FileSystem.SYSTEM,
            serializer = protobufSerializer(AppSettings(version = "")),
            producePath = {
                platformPathProducer.dataStoreFile("app_settings.pb")
            },
        )
}
