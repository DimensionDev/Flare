package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.common.CacheData
import dev.dimension.flare.ui.model.UiEmoji
import kotlinx.collections.immutable.ImmutableList

data class ComposeConfig(
    val text: Text? = null,
    val media: Media? = null,
    val poll: Poll? = null,
    val emoji: Emoji? = null,
    val contentWarning: ContentWarning? = null,
    val visibility: Visibility? = null,
) {
    data class Text(
        val maxLength: Int,
    ) {
        fun merge(other: Text): Text =
            Text(
                maxLength = minOf(maxLength, other.maxLength),
            )
    }

    data class Media(
        val maxCount: Int,
        val canSensitive: Boolean,
    ) {
        fun merge(other: Media): Media =
            Media(
                maxCount = minOf(maxCount, other.maxCount),
                canSensitive = canSensitive && other.canSensitive,
            )
    }

    data class Poll(
        val maxOptions: Int,
    ) {
        fun merge(other: Poll): Poll =
            Poll(
                maxOptions = minOf(maxOptions, other.maxOptions),
            )
    }

    data class Emoji(
        val emoji: CacheData<ImmutableList<UiEmoji>>,
        val mergeTag: String,
    ) {
        fun merge(other: Emoji): Emoji? =
            if (mergeTag == other.mergeTag) {
                Emoji(
                    emoji = emoji,
                    mergeTag = mergeTag,
                )
            } else {
                null
            }
    }

    object ContentWarning

    object Visibility

    fun merge(other: ComposeConfig): ComposeConfig {
        val text =
            if (text != null && other.text != null) {
                text.merge(other.text)
            } else {
                null
            }
        val media =
            if (media != null && other.media != null) {
                media.merge(other.media)
            } else {
                null
            }
        val poll =
            if (poll != null && other.poll != null) {
                poll.merge(other.poll)
            } else {
                null
            }
        val emoji =
            if (emoji != null && other.emoji != null) {
                emoji.merge(other.emoji)
            } else {
                null
            }
        val contentWarning =
            if (contentWarning != null && other.contentWarning != null) {
                contentWarning
            } else {
                null
            }
        return ComposeConfig(
            text = text,
            media = media,
            poll = poll,
            emoji = emoji,
            contentWarning = contentWarning,
            visibility = null,
        )
    }
}
