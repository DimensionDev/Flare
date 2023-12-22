package dev.dimension.flare.ui.presenter.status.action

import androidx.compose.runtime.Composable
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.repository.activeAccountServicePresenter
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class MastodonReportPresenter(
    private val userKey: MicroBlogKey,
    private val statusKey: MicroBlogKey?,
) : PresenterBase<MastodonReportState>() {
    @Composable
    override fun body(): MastodonReportState {
        val service =
            activeAccountServicePresenter().map { (service, _) ->
                service as MastodonDataSource
            }

        // using io scope because it's a long-running operation
        val scope = koinInject<CoroutineScope>()
        return object : MastodonReportState {
            override fun report() {
                service.onSuccess {
                    scope.launch {
                        it.report(userKey, statusKey)
                    }
                }
            }
        }
    }
}

interface MastodonReportState {
    fun report()
}
