package dev.dimension.flare.ui.presenter.status.action

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.contentPostOrNull
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.status.StatusPresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

public class BlueskyReportStatusPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<BlueskyReportStatusState>() {
    // using io scope because it's a long-running operation
    private val scope by koinInject<CoroutineScope>()
    private val accountService: AccountService by koinInject()
    private val serviceFlow by lazy {
        accountService.accountServiceFlow(accountType).map { service ->
            require(service is BlueskyDataSource)
            service
        }
    }

    @Composable
    override fun body(): BlueskyReportStatusState {
        val service by serviceFlow.collectAsUiState()
        val status =
            remember(statusKey, accountType) {
                StatusPresenter(accountType = accountType, statusKey = statusKey)
            }.body().status
        var reason by remember { mutableStateOf<BlueskyReportStatusState.ReportReason?>(null) }
        return object : BlueskyReportStatusState {
            override val allReasons = BlueskyReportStatusState.ReportReason.entries.toImmutableList()
            override val reason: BlueskyReportStatusState.ReportReason?
                get() = reason

            override val status: UiState<UiTimelineV2>
                get() =
                    status

            override fun report(
                value: BlueskyReportStatusState.ReportReason,
                status: UiTimelineV2,
            ) {
                service.onSuccess {
                    scope.launch {
                        val post = status.contentPostOrNull() ?: return@launch
                        it.report(post.statusKey, value)
                    }
                }
            }

            override fun selectReason(value: BlueskyReportStatusState.ReportReason) {
                reason = value
            }
        }
    }
}

@Immutable
public interface BlueskyReportStatusState {
    public val reason: ReportReason?
    public val status: UiState<UiTimelineV2>

    public val allReasons: ImmutableList<ReportReason>

    public enum class ReportReason {
        Spam,
        Violation,
        Misleading,
        Sexual,
        Rude,
        Other,
    }

    public fun selectReason(value: ReportReason)

    public fun report(
        value: ReportReason,
        status: UiTimelineV2,
    )
}
