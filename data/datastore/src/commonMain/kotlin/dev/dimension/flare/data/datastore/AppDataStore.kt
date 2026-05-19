package dev.dimension.flare.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import dev.dimension.flare.common.protobufSerializer
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.ComposeConfigData
import dev.dimension.flare.data.datastore.model.FlareConfig
import dev.dimension.flare.data.io.PlatformPathProducer

public class AppDataStore(
    private val platformPathProducer: PlatformPathProducer,
) {
    public val flareDataStore: DataStore<FlareConfig> by lazy {
        createDataStore(
            name = "flare_config.pb",
            defaultValue = FlareConfig(),
        )
    }

    public val composeConfigData: DataStore<ComposeConfigData> by lazy {
        createDataStore(
            name = "compose_config.pb",
            defaultValue = ComposeConfigData(),
        )
    }

    public val appSettingsStore: DataStore<AppSettings> by lazy {
        createDataStore(
            name = "app_settings.pb",
            defaultValue = AppSettings(version = ""),
        )
    }

    private inline fun <reified T> createDataStore(
        name: String,
        defaultValue: T,
    ): DataStore<T> =
        DataStoreFactory.create(
            storage =
                createDataStoreStorage(
                    name = name,
                    serializer = protobufSerializer(defaultValue),
                    platformPathProducer = platformPathProducer,
                ),
        )
}
