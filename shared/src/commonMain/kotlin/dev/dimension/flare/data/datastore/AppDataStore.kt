package dev.dimension.flare.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioSerializer
import dev.dimension.flare.common.protobufSerializer
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.ComposeConfigData
import dev.dimension.flare.data.datastore.model.FlareConfig
import dev.dimension.flare.data.datastore.model.PlatformOAuthPendingData
import dev.dimension.flare.data.io.FileStorage
import org.koin.core.annotation.Single

@Single
internal class AppDataStore(
    private val fileStorage: FileStorage,
) {
    val flareDataStore: DataStore<FlareConfig> by lazy {
        createDataStore(
            name = "flare_config.pb",
            serializer = protobufSerializer(FlareConfig()),
        )
    }

    val composeConfigData: DataStore<ComposeConfigData> by lazy {
        createDataStore(
            name = "compose_config.pb",
            serializer = protobufSerializer(ComposeConfigData()),
        )
    }

    val appSettingsStore: DataStore<AppSettings> by lazy {
        createDataStore(
            name = "app_settings.pb",
            serializer = protobufSerializer(AppSettings(version = "")),
        )
    }

    val platformOAuthPendingStore: DataStore<PlatformOAuthPendingData> by lazy {
        createDataStore(
            name = "platform_oauth_pending.pb",
            serializer = protobufSerializer(PlatformOAuthPendingData()),
        )
    }

    private fun <T> createDataStore(
        name: String,
        serializer: OkioSerializer<T>,
    ): DataStore<T> =
        DataStoreFactory.create(
            storage =
                createDataStoreStorage(
                    name = name,
                    serializer = serializer,
                    fileStorage = fileStorage,
                ),
        )
}
