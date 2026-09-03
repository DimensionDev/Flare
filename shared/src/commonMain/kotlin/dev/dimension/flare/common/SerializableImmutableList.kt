package dev.dimension.flare.common

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SealedSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.native.HiddenFromObjC

// https://github.com/Kotlin/kotlinx.collections.immutable/issues/63
public typealias SerializableImmutableList<T> =
    @Serializable(ImmutableListSerializer::class)
    ImmutableList<T>

@HiddenFromObjC
public class ImmutableListSerializer<T>(
    dataSerializer: KSerializer<T>,
) : KSerializer<ImmutableList<T>> {
    @OptIn(SealedSerializationApi::class)
    private class PersistentListDescriptor : SerialDescriptor by serialDescriptor<List<String>>() {
        override val serialName: String = "kotlinx.serialization.immutable.ImmutableList"
    }

    override val descriptor: SerialDescriptor = PersistentListDescriptor()
    private val delegate = ListSerializer(dataSerializer)

    override fun serialize(
        encoder: Encoder,
        value: ImmutableList<T>,
    ): Unit = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): ImmutableList<T> = delegate.deserialize(decoder).toPersistentList()
}

internal typealias SerializableImmutableMap<K, V> =
    @Serializable(ImmutableMapSerializer::class)
    ImmutableMap<K, V>

internal class ImmutableMapSerializer<K, V>(
    keySerializer: KSerializer<K>,
    valueSerializer: KSerializer<V>,
) : KSerializer<ImmutableMap<K, V>> {
    @OptIn(SealedSerializationApi::class)
    private class PersistentMapDescriptor : SerialDescriptor by serialDescriptor<Map<String, String>>() {
        override val serialName: String = "kotlinx.serialization.immutable.ImmutableMap"
    }

    override val descriptor: SerialDescriptor = PersistentMapDescriptor()
    private val delegate = MapSerializer(keySerializer, valueSerializer)

    override fun serialize(
        encoder: Encoder,
        value: ImmutableMap<K, V>,
    ) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): ImmutableMap<K, V> = delegate.deserialize(decoder).toPersistentMap()
}
