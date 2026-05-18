package dev.dimension.flare.common

import androidx.datastore.core.okio.OkioSerializer
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import kotlin.coroutines.CoroutineContext

public inline fun <reified T> protobufSerializer(defaultValue: T): OkioSerializer<T> = ProtobufSerializer(defaultValue, serializer<T>())

internal expect val protobufSerializerCoroutineContext: CoroutineContext

@OptIn(ExperimentalSerializationApi::class)
public class ProtobufSerializer<T>(
    override val defaultValue: T,
    public val serializer: kotlinx.serialization.KSerializer<T>,
) : OkioSerializer<T> {
    override suspend fun readFrom(source: okio.BufferedSource): T =
        withContext(protobufSerializerCoroutineContext) {
            ProtoBuf.decodeFromByteArray(serializer, source.readByteArray())
        }

    override suspend fun writeTo(
        t: T,
        sink: okio.BufferedSink,
    ) {
        sink.write(
            ProtoBuf.encodeToByteArray(serializer, t),
        )
    }
}
