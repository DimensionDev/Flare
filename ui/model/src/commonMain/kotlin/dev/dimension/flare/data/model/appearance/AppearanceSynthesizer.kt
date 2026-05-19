package dev.dimension.flare.data.model.appearance

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf

@OptIn(ExperimentalSerializationApi::class)
public fun AppearanceBag.toPatch(): AppearancePatch {
    var patch = AppearancePatch.EMPTY
    for ((id, bytes) in entries) {
        val key = AppearanceKeys[id] ?: continue
        val value =
            runCatching {
                ProtoBuf.decodeFromHexString(key.serializer, bytes)
            }.getOrNull() ?: continue
        @Suppress("UNCHECKED_CAST")
        patch = patch.set(key as AppearanceKey<Any>, value)
    }
    return patch
}

@OptIn(ExperimentalSerializationApi::class)
public fun AppearancePatch.toBag(): AppearanceBag =
    AppearanceBag(
        entries =
            explicitEntries
                .mapNotNull { (id, value) ->
                    val key = AppearanceKeys[id] ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    id to ProtoBuf.encodeToHexString(key.serializer as KSerializer<Any>, value)
                }.toMap(),
    )
