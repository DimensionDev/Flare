package dev.dimension.flare.ui.model

import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf

@Serializable
@OptIn(ExperimentalSerializationApi::class)
internal data class DeeplinkEvent(
    val accountKey: MicroBlogKey,
    val postEvent: PostEvent,
) {
    companion object {
        const val SCHEME = "flare-event"

        fun parse(uri: String): DeeplinkEvent? =
            runCatching {
                ProtoBuf.decodeFromHexString<DeeplinkEvent>(uri.removePrefix("$SCHEME://"))
            }.getOrNull()

        fun isDeeplinkEvent(uri: String): Boolean = uri.startsWith("$SCHEME://")
    }

    fun toUri(): String = "$SCHEME://${ProtoBuf.encodeToHexString(this)}"
}
