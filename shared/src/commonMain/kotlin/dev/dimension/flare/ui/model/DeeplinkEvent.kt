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
    val translationEvent: TranslationEvent? = null,
    val postEvent: PostEvent? = null,
) {
    init {
        require((translationEvent == null) xor (postEvent == null)) {
            "Exactly one deeplink event payload must be provided"
        }
    }

    companion object {
        const val SCHEME = "flare-event"

        fun parse(uri: String): DeeplinkEvent? =
            runCatching {
                ProtoBuf.decodeFromHexString<DeeplinkEvent>(uri.removePrefix("$SCHEME://"))
            }.getOrNull()

        fun isDeeplinkEvent(uri: String): Boolean = uri.startsWith("$SCHEME://")
    }

    fun toUri(): String = "$SCHEME://${ProtoBuf.encodeToHexString(this)}"

    @Serializable
    sealed interface TranslationEvent {
        @Serializable
        data class RetryTranslation(
            val statusKey: MicroBlogKey,
        ) : TranslationEvent

        @Serializable
        data class Translate(
            val statusKey: MicroBlogKey,
        ) : TranslationEvent

        @Serializable
        data class ShowOriginal(
            val statusKey: MicroBlogKey,
        ) : TranslationEvent
    }
}
