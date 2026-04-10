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
public data class DeeplinkEvent(
    val accountKey: MicroBlogKey,
    val translationEvent: TranslationEvent? = null,
    val postEvent: PostEvent? = null,
) {
    init {
        require((translationEvent == null) xor (postEvent == null)) {
            "Exactly one deeplink event payload must be provided"
        }
    }

    public companion object {
        public const val SCHEME: String = "flare-event"

        public fun parse(uri: String): DeeplinkEvent? =
            runCatching {
                ProtoBuf.decodeFromHexString<DeeplinkEvent>(uri.removePrefix("$SCHEME://"))
            }.getOrNull()

        public fun isDeeplinkEvent(uri: String): Boolean = uri.startsWith("$SCHEME://")
    }

    public fun toUri(): String = "$SCHEME://${ProtoBuf.encodeToHexString(this)}"

    @Serializable
    public sealed interface TranslationEvent {
        @Serializable
        public data class RetryTranslation(
            val statusKey: MicroBlogKey,
        ) : TranslationEvent

        @Serializable
        public data class Translate(
            val statusKey: MicroBlogKey,
        ) : TranslationEvent

        @Serializable
        public data class ShowOriginal(
            val statusKey: MicroBlogKey,
        ) : TranslationEvent
    }
}
