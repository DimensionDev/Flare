package dev.dimension.flare.ui.presenter.status.action

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.status.StatusPresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class BlueskyReportStatusPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<BlueskyReportStatusState>() {
    @Composable
    override fun body(): BlueskyReportStatusState {
        val service =
            accountServiceProvider(accountType = accountType).map { service ->
                service as BlueskyDataSource
            }
        val status =
            remember(statusKey, accountType) {
                StatusPresenter(accountType = accountType, statusKey = statusKey)
            }.body().status
        var reason by remember { mutableStateOf<BlueskyReportStatusState.ReportReason?>(null) }
        // using io scope because it's a long-running operation
        val scope = koinInject<CoroutineScope>()
        return object : BlueskyReportStatusState {
            override val allReasons = BlueskyReportStatusState.ReportReason.entries.toImmutableList()
            override val reason: BlueskyReportStatusState.ReportReason?
                get() = reason

            override val status: UiState<UiStatus.Bluesky>
                get() =
                    status.map {
                        it as UiStatus.Bluesky
                    }

            override fun report(
                value: BlueskyReportStatusState.ReportReason,
                status: UiStatus.Bluesky,
            ) {
                service.onSuccess {
                    scope.launch {
                        it.report(status, value)
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
    val status: UiState<UiStatus.Bluesky>

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
        status: UiStatus.Bluesky,
    )
}
