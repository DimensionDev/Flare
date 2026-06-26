package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.network.FaviconService
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase

public class FavIconPresenter(
    private val host: String,
) : PresenterBase<UiState<String>>() {
    private val cacheKey by lazy {
        "$host-favIcon"
    }

    private val flow by lazy {
        MemCacheable(cacheKey) {
            FaviconService.fetchIcon(host.toFavIconFetchUrl()) ?: throw IllegalStateException("Favicon not found")
        }
    }

    @Composable
    override fun body(): UiState<String> = flow.collectAsState().toUi()
}

internal fun String.toFavIconFetchUrl(): String =
    if (startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)) {
        this
    } else {
        "https://$this"
    }
