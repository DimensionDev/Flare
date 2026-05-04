package dev.dimension.flare.data.model.appearance

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class AppearanceBag(
    @ProtoNumber(1)
    val entries: Map<String, ByteArray> = emptyMap(),
    @ProtoNumber(2)
    val schemaVersion: Int = 2,
)
