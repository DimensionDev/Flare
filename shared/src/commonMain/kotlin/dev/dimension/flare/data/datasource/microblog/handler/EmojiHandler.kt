package dev.dimension.flare.data.datasource.microblog.handler

import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbEmoji
import dev.dimension.flare.data.database.cache.model.EmojiContent
import dev.dimension.flare.data.datasource.microblog.loader.EmojiLoader
import dev.dimension.flare.ui.model.UiEmoji
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class EmojiHandler(
    private val host: String,
    private val loader: EmojiLoader,
) : KoinComponent {
    private val database: CacheDatabase by inject()
    val emoji: Cacheable<ImmutableMap<String, ImmutableList<UiEmoji>>> =
        Cacheable(
            fetchSource = {
                val emojis = loader.emojis()
                database.emojiDao().insert(
                    DbEmoji(
                        host = host,
                        content =
                            EmojiContent(
                                emojis,
                            ),
                    ),
                )
            },
            cacheSource = {
                database
                    .emojiDao()
                    .get(host)
                    .distinctUntilChanged()
                    .mapNotNull {
                        it?.content?.data
                    }
            },
        )
}
