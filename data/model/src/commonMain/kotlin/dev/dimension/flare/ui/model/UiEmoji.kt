package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.SerializableImmutableList
import dev.dimension.flare.common.SerializableImmutableMap
import dev.dimension.flare.model.AccountType
import kotlinx.serialization.Serializable

@Serializable
@Immutable
public data class UiEmoji public constructor(
    val shortcode: String,
    val url: String,
    val category: String,
    val searchKeywords: SerializableImmutableList<String>,
    val insertText: String,
)

// compatibility class for Kotlin native
@Immutable
public data class EmojiData public constructor(
    val data: SerializableImmutableMap<String, SerializableImmutableList<UiEmoji>>,
    val accountType: AccountType,
) {
    private val list = data.toList()
    public val size: Int get() = data.size

    public fun getKey(index: Int): String = list[index].first

    public fun getValue(index: Int): SerializableImmutableList<UiEmoji> = list[index].second
}
