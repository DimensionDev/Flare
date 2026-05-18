package dev.dimension.flare.data.network.vvo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class EmojiData(
    val data: Data? = null,
    val ok: Long? = null,
)

@Serializable
public data class Data(
    val emoticon: Emoticon? = null,
)

@Serializable
public data class Emoticon(
    @SerialName("ZH_CN")
    val zhCN: Map<String, List<Zh>>? = null,
    @SerialName("ZH_TW")
    val zhTw: Map<String, List<Zh>>? = null,
)

@Serializable
public data class Zh(
    val phrase: String? = null,
    val url: String? = null,
)
