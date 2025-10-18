package dev.dimension.flare.ui.presenter.status.action

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class MastodonReportPresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey,
    private val statusKey: MicroBlogKey?,
) : PresenterBase<MastodonReportState>(),
    KoinComponent {
    // using io scope because it's a long-running operation
    private val scope by inject<CoroutineScope>()
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): MastodonReportState =
        object : MastodonReportState {
            override fun report() {
                scope.launch {
                    accountServiceFlow(
                        accountType = accountType,
                        repository = accountRepository,
                    ).map {
                        require(it is MastodonDataSource)
                        it
                    }.first()
                        .report(userKey, statusKey)
                }
            }
        }
}

@Immutable
public interface MastodonReportState {
    public fun report()
}
