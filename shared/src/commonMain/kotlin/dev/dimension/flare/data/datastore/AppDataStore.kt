package dev.dimension.flare.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import dev.dimension.flare.data.datastore.model.FlareConfig
import dev.dimension.flare.data.datastore.model.FlareConfigSerializer
import dev.dimension.flare.data.datastore.model.GuestData
import dev.dimension.flare.data.datastore.model.GuestDataSerializer
import dev.dimension.flare.data.io.PlatformPathProducer
import okio.FileSystem
import okio.SYSTEM

internal class AppDataStore(
    private val platformPathProducer: PlatformPathProducer,
) {
    val guestDataStore: DataStore<GuestData> by lazy {
        DataStoreFactory.create(
            storage =
                OkioStorage(
                    fileSystem = FileSystem.SYSTEM,
                    serializer = GuestDataSerializer,
                    producePath = {
                        platformPathProducer.dataStoreFile("guest_data.pb")
                    },
                ),
        )
    }

    val flareDataStore: DataStore<FlareConfig> by lazy {
        DataStoreFactory.create(
            storage =
                OkioStorage(
                    fileSystem = FileSystem.SYSTEM,
                    serializer = FlareConfigSerializer,
                    producePath = {
                        platformPathProducer.dataStoreFile("flare_config.pb")
                    },
                ),
        )
    }
}
