package dev.dimension.flare.ui.presenter.status.action

import androidx.compose.runtime.Composable
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.repository.activeAccountServicePresenter
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.rememberKoinInject

class MisskeyReportPresenter(
    private val userKey: MicroBlogKey,
    private val statusKey: MicroBlogKey?,
) : PresenterBase<MisskeyReportState>() {
    @Composable
    override fun body(): MisskeyReportState {
        val service =
            activeAccountServicePresenter().map { (service, _) ->
                service as MisskeyDataSource
            }

        // using io scope because it's a long-running operation
        val scope = rememberKoinInject<CoroutineScope>()
        return object : MisskeyReportState {
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

interface MisskeyReportState {
    fun report()
}
