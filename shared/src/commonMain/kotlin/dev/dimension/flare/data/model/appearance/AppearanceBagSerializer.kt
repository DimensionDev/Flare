package dev.dimension.flare.data.model.appearance

import androidx.datastore.core.okio.OkioSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okio.BufferedSink
import okio.BufferedSource

@OptIn(ExperimentalSerializationApi::class)
internal object AppearanceBagSerializer : OkioSerializer<AppearanceBag> {
    override val defaultValue: AppearanceBag = AppearanceBag()

    override suspend fun readFrom(source: BufferedSource): AppearanceBag =
        withContext(Dispatchers.IO) {
            val bytes = source.readByteArray()
            if (bytes.isEmpty()) {
                defaultValue
            } else {
                runCatching {
                    ProtoBuf.decodeFromByteArray<AppearanceBag>(bytes)
                }.getOrDefault(defaultValue)
            }
        }

    override suspend fun writeTo(
        t: AppearanceBag,
        sink: BufferedSink,
    ) {
        withContext(Dispatchers.IO) {
            sink.write(ProtoBuf.encodeToByteArray(t))
        }
    }
}
