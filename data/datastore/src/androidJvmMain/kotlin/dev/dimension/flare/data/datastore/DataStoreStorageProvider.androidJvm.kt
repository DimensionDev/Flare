package dev.dimension.flare.data.datastore

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.core.okio.OkioSerializer
import dev.dimension.flare.common.protobufSerializer
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.ComposeConfigData
import dev.dimension.flare.data.datastore.model.FlareConfig
import dev.dimension.flare.data.io.PlatformPathProducer
import okio.FileSystem
import okio.SYSTEM

public actual class DataStoreStorageProvider actual constructor(
    private val platformPathProducer: PlatformPathProducer,
) {
    public actual fun <T> storage(
        name: String,
        serializer: OkioSerializer<T>,
    ): Storage<T> =
        OkioStorage(
            fileSystem = FileSystem.SYSTEM,
            serializer = serializer,
            producePath = {
                platformPathProducer.dataStoreFile(name)
            },
        )

    public actual fun flareConfigStorage(): Storage<FlareConfig> =
        storage(
            name = "flare_config.pb",
            serializer = protobufSerializer(FlareConfig()),
        )

    public actual fun composeConfigStorage(): Storage<ComposeConfigData> =
        storage(
            name = "compose_config.pb",
            serializer = protobufSerializer(ComposeConfigData()),
        )

    public actual fun appSettingsStorage(): Storage<AppSettings> =
        storage(
            name = "app_settings.pb",
            serializer = protobufSerializer(AppSettings(version = "")),
        )
}
