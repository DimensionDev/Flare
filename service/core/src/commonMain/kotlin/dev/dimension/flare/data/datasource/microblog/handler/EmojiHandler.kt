package dev.dimension.flare.data.datasource.microblog.handler

import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.decodeProtobuf
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbEmoji
import dev.dimension.flare.data.database.cache.model.EmojiContent
import dev.dimension.flare.data.datasource.microblog.loader.EmojiLoader
import dev.dimension.flare.ui.model.UiEmoji
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transform
import kotlinx.serialization.SerializationException
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
                if (emojis.isEmpty()) {
                    database.emojiDao().delete(host)
                } else {
                    database.emojiDao().insert(
                        DbEmoji(
                            host = host,
                            content =
                                EmojiContent(
                                    emojis,
                                ),
                        ),
                    )
                }
            },
            cacheSource = {
                database
                    .emojiDao()
                    .getContent(host)
                    .distinctUntilChanged()
                    .transform { content ->
                        if (content == null) {
                            return@transform
                        }
                        val decoded =
                            runCatching {
                                if (content.isEmpty()) {
                                    EmojiContent()
                                } else {
                                    content.decodeProtobuf<EmojiContent>()
                                }
                            }.getOrElse { throwable ->
                                if (throwable is SerializationException) {
                                    database.emojiDao().delete(host)
                                    return@transform
                                }
                                throw throwable
                            }
                        emit(decoded.data)
                    }
            },
        )
}
