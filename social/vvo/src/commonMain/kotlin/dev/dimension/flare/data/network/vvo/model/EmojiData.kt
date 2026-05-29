package dev.dimension.flare.data.network.vvo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class EmojiData(
    val data: Data? = null,
    val ok: Long? = null,
)

@Serializable
internal data class Data(
    val emoticon: Emoticon? = null,
)

@Serializable
internal data class Emoticon(
    @SerialName("ZH_CN")
    val zhCN: Map<String, List<Zh>>? = null,
    @SerialName("ZH_TW")
    val zhTw: Map<String, List<Zh>>? = null,
)

@Serializable
internal data class Zh(
    val phrase: String? = null,
    val url: String? = null,
)
