package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.network.rss.RssService
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import org.koin.core.component.KoinComponent

public class FavIconPresenter(
    private val host: String,
) : PresenterBase<UiState<String>>(),
    KoinComponent {
    private val cacheKey by lazy {
        "$host-favIcon"
    }

    private val flow by lazy {
        MemCacheable(cacheKey) {
            RssService.fetchIcon("https://$host") ?: throw IllegalStateException("Favicon not found")
        }
    }

    @Composable
    override fun body(): UiState<String> = flow.collectAsState().toUi()
}
