package dev.dimension.flare.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import dev.dimension.flare.data.datastore.model.GuestData
import dev.dimension.flare.data.datastore.model.GuestDataSerializer
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

internal class AppDataStore(
    private val producePath: (fileName: String) -> String,
) {
    val guestDataStore: DataStore<GuestData> by lazy {
        DataStoreFactory.create(
            storage =
                OkioStorage(
                    fileSystem = FileSystem.SYSTEM,
                    serializer = GuestDataSerializer,
                    producePath = {
                        producePath.invoke("guest_data.pb").toPath()
                    },
                ),
        )
    }
}
