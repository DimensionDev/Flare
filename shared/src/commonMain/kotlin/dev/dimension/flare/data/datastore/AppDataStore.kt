package dev.dimension.flare.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import dev.dimension.flare.common.protobufSerializer
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.ComposeConfigData
import dev.dimension.flare.data.datastore.model.FlareConfig
import dev.dimension.flare.data.io.PlatformPathProducer
import okio.FileSystem
import okio.SYSTEM

internal class AppDataStore(
    private val platformPathProducer: PlatformPathProducer,
) {
    val flareDataStore: DataStore<FlareConfig> by lazy {
        DataStoreFactory.create(
            storage =
                OkioStorage(
                    fileSystem = FileSystem.SYSTEM,
                    serializer = protobufSerializer(FlareConfig()),
                    producePath = {
                        platformPathProducer.dataStoreFile("flare_config.pb")
                    },
                ),
        )
    }

    val composeConfigData: DataStore<ComposeConfigData> by lazy {
        DataStoreFactory.create(
            storage =
                OkioStorage(
                    fileSystem = FileSystem.SYSTEM,
                    serializer = protobufSerializer(ComposeConfigData()),
                    producePath = {
                        platformPathProducer.dataStoreFile("compose_config.pb")
                    },
                ),
        )
    }

    val appSettingsStore: DataStore<AppSettings> by lazy {
        DataStoreFactory.create(
            storage =
                OkioStorage(
                    fileSystem = FileSystem.SYSTEM,
                    serializer = protobufSerializer(AppSettings(version = "")),
                    producePath = {
                        platformPathProducer.dataStoreFile("app_settings.pb")
                    },
                ),
        )
    }
}
