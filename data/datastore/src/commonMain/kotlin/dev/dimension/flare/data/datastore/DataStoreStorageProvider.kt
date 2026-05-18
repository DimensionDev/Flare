package dev.dimension.flare.data.datastore

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioSerializer
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.ComposeConfigData
import dev.dimension.flare.data.datastore.model.FlareConfig
import dev.dimension.flare.data.io.PlatformPathProducer

public expect class DataStoreStorageProvider(
    platformPathProducer: PlatformPathProducer,
) {
    public fun <T> storage(
        name: String,
        serializer: OkioSerializer<T>,
    ): Storage<T>

    public fun flareConfigStorage(): Storage<FlareConfig>

    public fun composeConfigStorage(): Storage<ComposeConfigData>

    public fun appSettingsStorage(): Storage<AppSettings>
}
