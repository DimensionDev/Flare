package dev.dimension.flare.ui.presenter.status.action

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

public class MastodonReportPresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey,
    private val statusKey: MicroBlogKey?,
) : PresenterBase<MastodonReportState>() {
    // using io scope because it's a long-running operation
    private val scope by koinInject<CoroutineScope>()
    private val accountService: AccountService by koinInject()

    @Composable
    override fun body(): MastodonReportState =
        object : MastodonReportState {
            override fun report() {
                scope.launch {
                    accountService
                        .accountServiceFlow(accountType)
                        .mapNotNull {
                            it as? MastodonDataSource
                        }.firstOrNull()
                        ?.report(userKey, statusKey)
                }
            }
        }
}

@Immutable
public interface MastodonReportState {
    public fun report()
}
