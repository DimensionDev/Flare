package dev.dimension.flare.data.datastore

import androidx.datastore.core.Storage
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.ComposeConfigData
import dev.dimension.flare.data.datastore.model.FlareConfig
import dev.dimension.flare.data.io.PlatformPathProducer

public expect class DataStoreStorageProvider(
    platformPathProducer: PlatformPathProducer,
) {
    public fun flareConfigStorage(): Storage<FlareConfig>

    public fun composeConfigStorage(): Storage<ComposeConfigData>

    public fun appSettingsStorage(): Storage<AppSettings>
}
