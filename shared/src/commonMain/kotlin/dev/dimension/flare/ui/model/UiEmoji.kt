package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.SerializableImmutableList
import dev.dimension.flare.common.SerializableImmutableMap
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.model.AccountType
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@Immutable
public data class UiEmoji public constructor(
    val shortcode: String,
    val url: String,
    val category: String,
    val searchKeywords: SerializableImmutableList<String>,
    val insertText: String,
    @Transient val proxy: ProxyRef = ProxyRef.Unbound,
) {
    val media: UiMedia.Image get() = UiMedia.Image(url, proxy = proxy)
}

// compatibility class for Kotlin native
@Immutable
public data class EmojiData internal constructor(
    private val unboundData: SerializableImmutableMap<String, SerializableImmutableList<UiEmoji>>,
    val accountType: AccountType,
) {
    public val data: SerializableImmutableMap<String, SerializableImmutableList<UiEmoji>> =
        unboundData
            .mapValues { (_, values) ->
                values.map { it.copy(proxy = ProxyRef.forAccountType(accountType)) }.toImmutableList()
            }.toImmutableMap()
    private val list = this.data.toList()
    public val size: Int get() = data.size

    public fun getKey(index: Int): String = list[index].first

    public fun getValue(index: Int): SerializableImmutableList<UiEmoji> = list[index].second
}
