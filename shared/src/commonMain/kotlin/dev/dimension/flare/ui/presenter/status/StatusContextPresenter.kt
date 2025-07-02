package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.common.BaseTimelineLoader
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import dev.dimension.flare.ui.presenter.home.TimelineState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class StatusContextPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<TimelineState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): TimelineState {
        val listState = remember(
            accountType,
            statusKey,
        ) {
            object : TimelinePresenter() {
                override val loader: Flow<BaseTimelineLoader> by lazy {
                    accountServiceFlow(
                        accountType = accountType,
                        repository = accountRepository,
                    ).map { service ->
                        require(service is BaseTimelineLoader)
                        service.context(statusKey)
                    }
                }
            }
        }.body()
        remember { LogStatusHistoryPresenter(accountType = accountType, statusKey = statusKey) }.body()
        return listState
    }
}
