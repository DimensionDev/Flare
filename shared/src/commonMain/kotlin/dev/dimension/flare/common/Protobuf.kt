package dev.dimension.flare.common

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf

@OptIn(ExperimentalSerializationApi::class)
internal inline fun <reified T> T.encodeProtobuf(): ByteArray = ProtoBuf.encodeToByteArray(this)

@OptIn(ExperimentalSerializationApi::class)
internal inline fun <reified T> ByteArray.decodeProtobuf(): T = ProtoBuf.decodeFromByteArray(this)

@OptIn(ExperimentalSerializationApi::class)
internal inline fun <reified T> String.decodeProtobuf(): T = ProtoBuf.decodeFromHexString(this)

@OptIn(ExperimentalSerializationApi::class)
internal inline fun <reified T> String.decodeProtobuf(serializer: KSerializer<T>): T =
    ProtoBuf.decodeFromHexString(serializer, this)

@OptIn(ExperimentalSerializationApi::class)
internal inline fun <reified T> T.encodeProtobufToString(): String =
    ProtoBuf.encodeToHexString(this)

@OptIn(ExperimentalSerializationApi::class)
internal inline fun <reified T> T.encodeProtobufToString(serializer: KSerializer<T>): String =
    ProtoBuf.encodeToHexString(serializer, this)