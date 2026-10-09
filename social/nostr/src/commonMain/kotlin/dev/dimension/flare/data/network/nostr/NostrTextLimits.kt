package dev.dimension.flare.data.network.nostr

import de.cketti.codepoints.codePointCount

internal data class NostrTextLimits(
    val maxContentLength: Int? = null,
    val maxMessageLength: Int? = null,
) {
    fun error(
        content: String,
        eventJson: String,
    ): String? =
        when {
            maxContentLength != null && content.codePointCount() > maxContentLength -> {
                "Event content exceeds the relay character limit."
            }

            maxMessageLength != null && ("[\"EVENT\"," + eventJson + "]").encodeToByteArray().size > maxMessageLength -> {
                "Event message exceeds the relay UTF-8 byte limit."
            }

            else -> {
                null
            }
        }
}
