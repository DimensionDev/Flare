package dev.dimension.flare.ui.presenter.status.action

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class MisskeyReportPresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey,
    private val statusKey: MicroBlogKey?,
) : PresenterBase<MisskeyReportState>(),
    KoinComponent {
    // using io scope because it's a long-running operation
    private val scope by inject<CoroutineScope>()
    private val accountService: AccountService by inject()

    @Composable
    override fun body(): MisskeyReportState =
        object : MisskeyReportState {
            override fun report(comment: String) {
                scope.launch {
                    accountService.accountServiceFlow(accountType).mapNotNull {
                        it as? MisskeyDataSource
                    }.firstOrNull()
                        ?.report(userKey, statusKey, comment)
                }
            }
        }
}

@Immutable
public interface MisskeyReportState {
    public fun report(comment: String)
}
