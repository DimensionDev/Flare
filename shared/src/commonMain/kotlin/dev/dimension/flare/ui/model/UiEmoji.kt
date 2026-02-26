package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.SerializableImmutableList
import dev.dimension.flare.common.SerializableImmutableMap

@Immutable
public data class UiEmoji internal constructor(
    val shortcode: String,
    val url: String,
    val category: String,
    val searchKeywords: List<String>,
    val insertText: String,
)

// compatibility class for Kotlin native
@Immutable
public data class EmojiData internal constructor(
    val data: SerializableImmutableMap<String, SerializableImmutableList<UiEmoji>>,
) {
    private val list = data.toList()
    public val size: Int get() = data.size

    public fun getKey(index: Int): String = list[index].first

    public fun getValue(index: Int): SerializableImmutableList<UiEmoji> = list[index].second
}
