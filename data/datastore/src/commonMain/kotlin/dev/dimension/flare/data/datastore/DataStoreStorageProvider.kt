package dev.dimension.flare.data.datastore

import androidx.datastore.core.Storage
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.ComposeConfigData
import dev.dimension.flare.data.datastore.model.FlareConfig
import dev.dimension.flare.data.io.PlatformPathProducer

internal expect class DataStoreStorageProvider(
    platformPathProducer: PlatformPathProducer,
) {
    fun flareConfigStorage(): Storage<FlareConfig>

    fun composeConfigStorage(): Storage<ComposeConfigData>

    fun appSettingsStorage(): Storage<AppSettings>
}
