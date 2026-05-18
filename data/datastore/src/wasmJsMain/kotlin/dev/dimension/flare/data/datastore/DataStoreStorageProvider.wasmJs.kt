package dev.dimension.flare.data.datastore

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.WebOpfsStorage
import dev.dimension.flare.common.protobufSerializer
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.ComposeConfigData
import dev.dimension.flare.data.datastore.model.FlareConfig
import dev.dimension.flare.data.io.PlatformPathProducer

public actual class DataStoreStorageProvider actual constructor(
    @Suppress("UNUSED_PARAMETER") platformPathProducer: PlatformPathProducer,
) {
    public actual fun flareConfigStorage(): Storage<FlareConfig> =
        WebOpfsStorage(
            serializer = protobufSerializer(FlareConfig()),
            name = "flare_config.pb",
        )

    public actual fun composeConfigStorage(): Storage<ComposeConfigData> =
        WebOpfsStorage(
            serializer = protobufSerializer(ComposeConfigData()),
            name = "compose_config.pb",
        )

    public actual fun appSettingsStorage(): Storage<AppSettings> =
        WebOpfsStorage(
            serializer = protobufSerializer(AppSettings(version = "")),
            name = "app_settings.pb",
        )
}
