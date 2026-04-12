package dev.dimension.flare.data.datasource.microblog.loader

import dev.dimension.flare.ui.model.UiEmoji
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

public interface EmojiLoader {
    public suspend fun emojis(): ImmutableMap<String, ImmutableList<UiEmoji>>
}
