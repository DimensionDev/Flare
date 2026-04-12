package dev.dimension.flare.data.datasource.microblog.loader

import dev.dimension.flare.ui.model.UiEmoji
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

internal interface EmojiLoader {
    suspend fun emojis(): ImmutableMap<String, ImmutableList<UiEmoji>>
}
