package dev.dimension.flare.feature.agent.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.feature.agent.status.StatusInsightAgentUseCase
import dev.dimension.flare.feature.agent.status.StatusInsightEvent
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class StatusInsightPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<StatusInsightPresenter.State>(),
    KoinComponent {
    private val accountService: AccountService by inject()
    private val statusInsightAgentUseCase: StatusInsightAgentUseCase by inject()

    @Immutable
    public interface State {
        public val insight: UiState<String>
        public val post: UiTimelineV2.Post?
        public val currentTrace: StatusInsightEvent.Trace?
    }

    @Composable
    override fun body(): State {
        val state =
            produceState<State>(
                initialValue = StateImpl(),
                accountType,
                statusKey,
            ) {
                accountService
                    .accountServiceFlow(accountType)
                    .combine(accountService.allAccountServicesFlow()) { service, searchDataSources ->
                        service to searchDataSources
                    }.collectLatest { (service, searchDataSources) ->
                        var post: UiTimelineV2.Post? = null
                        var currentTrace: StatusInsightEvent.Trace? = null

                        fun update(
                            insight: UiState<String>,
                            postValue: UiTimelineV2.Post? = post,
                            currentTraceValue: StatusInsightEvent.Trace? = currentTrace,
                        ) {
                            value =
                                StateImpl(
                                    insight = insight,
                                    post = postValue,
                                    currentTrace = currentTraceValue,
                                )
                        }

                        update(UiState.Loading())

                        val postDataSource =
                            service as? PostDataSource
                                ?: run {
                                    update(UiState.Error(IllegalStateException("Current account does not support post data source")))
                                    return@collectLatest
                                }

                        try {
                            statusInsightAgentUseCase(
                                postDataSource = postDataSource,
                                statusKey = statusKey,
                                searchDataSources = searchDataSources,
                            ).collect { event ->
                                when (event) {
                                    is StatusInsightEvent.PostLoaded -> {
                                        post = event.post
                                        update(UiState.Loading())
                                    }

                                    is StatusInsightEvent.Trace -> {
                                        currentTrace = event
                                        update(UiState.Loading())
                                    }

                                    is StatusInsightEvent.Result -> {
                                        currentTrace = null
                                        update(UiState.Success(event.text))
                                    }
                                }
                            }
                        } catch (throwable: Throwable) {
                            currentTrace = null
                            update(UiState.Error(throwable))
                        }
                    }
            }

        return state.value
    }

    @Immutable
    private data class StateImpl(
        override val insight: UiState<String> = UiState.Loading(),
        override val post: UiTimelineV2.Post? = null,
        override val currentTrace: StatusInsightEvent.Trace? = null,
    ) : State
}
