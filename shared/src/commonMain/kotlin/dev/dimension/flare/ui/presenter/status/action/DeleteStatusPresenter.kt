package dev.dimension.flare.ui.presenter.status.action

import androidx.compose.runtime.Composable
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class DeleteStatusPresenter(
    private val accountKey: MicroBlogKey,
    private val statusKey: MicroBlogKey,
) : PresenterBase<DeleteStatusState>() {
    @Composable
    override fun body(): DeleteStatusState {
        val service = accountServiceProvider(accountKey = accountKey)
        // using io scope because it's a long-running operation
        val scope = koinInject<CoroutineScope>()
        return object : DeleteStatusState {
            override fun delete() {
                service.onSuccess {
                    scope.launch {
                        it.deleteStatus(statusKey)
                    }
                }
            }
        }
    }
}

interface DeleteStatusState {
    fun delete()
}
