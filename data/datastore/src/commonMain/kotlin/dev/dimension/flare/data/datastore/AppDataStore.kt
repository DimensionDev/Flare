package dev.dimension.flare.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.Storage
import androidx.datastore.core.StorageConnection
import androidx.datastore.core.okio.OkioSerializer
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.AppSettingsSerializer
import dev.dimension.flare.data.datastore.model.ComposeConfigData
import dev.dimension.flare.data.datastore.model.ComposeConfigDataSerializer
import dev.dimension.flare.data.datastore.model.FlareConfig
import dev.dimension.flare.data.datastore.model.FlareConfigSerializer
import dev.dimension.flare.data.io.PlatformPathProducer
import kotlin.coroutines.CoroutineContext

internal expect class PlatformStorage<T>(
    fileName: String,
    serializer: OkioSerializer<T>,
    platformPathProducer: PlatformPathProducer,
) : Storage<T> {
    override fun createConnection(): StorageConnection<T>
}
internal expect val storeContext: CoroutineContext

public class AppDataStore(
    private val platformPathProducer: PlatformPathProducer,
) {
    public val flareDataStore: DataStore<FlareConfig> by lazy {
        DataStore
            .Builder(
                PlatformStorage(
                    "flare_config.pb",
                    FlareConfigSerializer,
                    platformPathProducer,
                ),
                storeContext,
            )
            .build()
    }

    public val composeConfigData: DataStore<ComposeConfigData> by lazy {
        DataStore
            .Builder(
                PlatformStorage(
                    "compose_config.pb",
                    ComposeConfigDataSerializer,
                    platformPathProducer,
                ),
                storeContext,
            )
            .build()
    }

    public val appSettingsStore: DataStore<AppSettings> by lazy {
        DataStore
            .Builder(
                PlatformStorage(
                    "app_settings.pb",
                    AppSettingsSerializer,
                    platformPathProducer,
                ),
                storeContext,
            )
            .build()
    }
}
