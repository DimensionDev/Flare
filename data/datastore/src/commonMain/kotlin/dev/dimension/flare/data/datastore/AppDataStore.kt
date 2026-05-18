package dev.dimension.flare.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.ComposeConfigData
import dev.dimension.flare.data.datastore.model.FlareConfig
import dev.dimension.flare.data.io.PlatformPathProducer

public class AppDataStore(
    private val storageProvider: DataStoreStorageProvider,
) {
    public constructor(
        platformPathProducer: PlatformPathProducer,
    ) : this(DataStoreStorageProvider(platformPathProducer))

    public val flareDataStore: DataStore<FlareConfig> by lazy {
        DataStoreFactory.create(
            storage = storageProvider.flareConfigStorage(),
        )
    }

    public val composeConfigData: DataStore<ComposeConfigData> by lazy {
        DataStoreFactory.create(
            storage = storageProvider.composeConfigStorage(),
        )
    }

    public val appSettingsStore: DataStore<AppSettings> by lazy {
        DataStoreFactory.create(
            storage = storageProvider.appSettingsStorage(),
        )
    }
}
