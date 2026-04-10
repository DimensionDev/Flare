package dev.dimension.flare.data.datastore

import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import dev.dimension.flare.data.io.PlatformPathProducer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import okio.FileSystem
import okio.SYSTEM
import kotlin.coroutines.CoroutineContext

internal actual class PlatformStorage<T> actual constructor(
    fileName: String,
    serializer: OkioSerializer<T>,
    platformPathProducer: PlatformPathProducer,
) : androidx.datastore.core.Storage<T> by OkioStorage(
        FileSystem.SYSTEM,
        serializer = serializer,
        producePath = {
            platformPathProducer.dataStoreFile(fileName)
        },
    )

internal actual val storeContext: CoroutineContext = Dispatchers.IO + SupervisorJob()
