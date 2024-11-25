package dev.dimension.flare.ui.presenter.status.action

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.status.StatusPresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BlueskyReportStatusPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<BlueskyReportStatusState>(),
    KoinComponent {
    // using io scope because it's a long-running operation
    private val scope by inject<CoroutineScope>()
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): BlueskyReportStatusState {
        val service =
            accountServiceProvider(accountType = accountType, repository = accountRepository).map { service ->
                service as BlueskyDataSource
            }
        val status =
            remember(statusKey, accountType) {
                StatusPresenter(accountType = accountType, statusKey = statusKey)
            }.body().status
        var reason by remember { mutableStateOf<BlueskyReportStatusState.ReportReason?>(null) }
        return object : BlueskyReportStatusState {
            override val allReasons = BlueskyReportStatusState.ReportReason.entries.toImmutableList()
            override val reason: BlueskyReportStatusState.ReportReason?
                get() = reason

            override val status: UiState<UiTimeline>
                get() =
                    status

            override fun report(
                value: BlueskyReportStatusState.ReportReason,
                status: UiTimeline,
            ) {
                service.onSuccess {
                    scope.launch {
                        if (status.content is UiTimeline.ItemContent.Status) {
                            it.report(status.content.statusKey, value)
                        }
                    }
                }
            }

            override fun selectReason(value: BlueskyReportStatusState.ReportReason) {
                reason = value
            }
        }
    }
}

interface BlueskyReportStatusState {
    val reason: ReportReason?
    val status: UiState<UiTimeline>

    val allReasons: ImmutableList<ReportReason>

    enum class ReportReason {
        Spam,
        Violation,
        Misleading,
        Sexual,
        Rude,
        Other,
    }

    fun selectReason(value: ReportReason)

    fun report(
        value: ReportReason,
        status: UiTimeline,
    )
}
