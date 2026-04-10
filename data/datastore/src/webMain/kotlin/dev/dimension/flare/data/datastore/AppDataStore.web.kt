package dev.dimension.flare.data.datastore

import androidx.datastore.core.okio.OkioSerializer
import dev.dimension.flare.data.io.PlatformPathProducer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

internal actual class PlatformStorage<T> actual constructor(
    fileName: String,
    serializer: OkioSerializer<T>,
    platformPathProducer: PlatformPathProducer,
) : androidx.datastore.core.Storage<T> by androidx.datastore.core.okio.WebStorage(
    serializer = serializer,
    name = fileName,
    storageType = androidx.datastore.core.okio.WebStorageType.LOCAL
)

internal actual val storeContext: CoroutineContext = Dispatchers.Default + SupervisorJob()
