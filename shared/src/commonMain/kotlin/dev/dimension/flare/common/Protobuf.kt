package dev.dimension.flare.common

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf

@OptIn(ExperimentalSerializationApi::class)
internal inline fun <reified T> T.encodeProtobuf(): ByteArray = ProtoBuf.encodeToByteArray(this)

@OptIn(ExperimentalSerializationApi::class)
internal inline fun <reified T> ByteArray.decodeProtobuf(): T = ProtoBuf.decodeFromByteArray(this)
