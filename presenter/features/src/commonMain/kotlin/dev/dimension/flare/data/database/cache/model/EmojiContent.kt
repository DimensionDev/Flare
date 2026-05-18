package dev.dimension.flare.data.database.cache.model

import dev.dimension.flare.common.SerializableImmutableList
import dev.dimension.flare.common.SerializableImmutableMap
import dev.dimension.flare.ui.model.UiEmoji
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.serialization.Serializable

@Serializable
internal data class EmojiContent(
    val data: SerializableImmutableMap<String, SerializableImmutableList<UiEmoji>> = persistentMapOf(),
)
