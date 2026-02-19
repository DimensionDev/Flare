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

// https://github.com/Kotlin/kotlinx.collections.immutable/issues/63
internal typealias SerializableImmutableList<T> =
    @Serializable(ImmutableListSerializer::class)
    ImmutableList<T>

internal class ImmutableListSerializer<T>(
    private val dataSerializer: KSerializer<T>,
) : KSerializer<ImmutableList<T>> {
    @OptIn(SealedSerializationApi::class)
    private class PersistentListDescriptor : SerialDescriptor by serialDescriptor<List<String>>() {
        override val serialName: String = "kotlinx.serialization.immutable.ImmutableList"
    }

    override val descriptor: SerialDescriptor = PersistentListDescriptor()

    override fun serialize(
        encoder: Encoder,
        value: ImmutableList<T>,
    ) = ListSerializer(dataSerializer).serialize(encoder, value.toList())

    override fun deserialize(decoder: Decoder): ImmutableList<T> = ListSerializer(dataSerializer).deserialize(decoder).toPersistentList()
}

internal typealias SerializableImmutableMap<K, V> =
    @Serializable(ImmutableMapSerializer::class)
    ImmutableMap<K, V>

internal class ImmutableMapSerializer<K, V>(
    private val keySerializer: KSerializer<K>,
    private val valueSerializer: KSerializer<V>,
) : KSerializer<ImmutableMap<K, V>> {
    @OptIn(SealedSerializationApi::class)
    private class PersistentMapDescriptor : SerialDescriptor by serialDescriptor<Map<String, String>>() {
        override val serialName: String = "kotlinx.serialization.immutable.ImmutableMap"
    }

    override val descriptor: SerialDescriptor = PersistentMapDescriptor()

    override fun serialize(
        encoder: Encoder,
        value: ImmutableMap<K, V>,
    ) = MapSerializer(keySerializer, valueSerializer).serialize(encoder, value.toMap())

    override fun deserialize(decoder: Decoder): ImmutableMap<K, V> =
        MapSerializer(keySerializer, valueSerializer).deserialize(decoder).toPersistentMap()
}
