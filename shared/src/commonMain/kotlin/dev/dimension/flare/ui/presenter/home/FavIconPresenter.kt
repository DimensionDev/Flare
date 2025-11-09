package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbEmoji
import dev.dimension.flare.data.database.cache.model.EmojiContent
import dev.dimension.flare.data.network.rss.RssService
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class FavIconPresenter(
    private val host: String,
) : PresenterBase<UiState<String>>(),
    KoinComponent {
    private val dbKey by lazy {
        "$host-favIcon"
    }

    private val database: CacheDatabase by inject()
    private val flow by lazy {
        Cacheable(
            fetchSource = {
                val favIcon = RssService.fetchIcon("https://$host")
                if (favIcon == null) {
                    throw IllegalStateException("Favicon not found")
                } else {
                    database.emojiDao().insert(
                        DbEmoji(
                            host = dbKey,
                            content = EmojiContent.FavIcon(favIcon),
                        ),
                    )
                }
            },
            cacheSource = {
                database
                    .emojiDao()
                    .get(dbKey)
                    .mapNotNull {
                        it?.content as? EmojiContent.FavIcon
                    }.map {
                        it.data
                    }
            },
        )
    }

    @Composable
    override fun body(): UiState<String> = flow.collectAsState().toUi()
}
