package dev.dimension.flare.ui.presenter.podcast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.data.datasource.xqt.XQTDataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiPodcast
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.flow
import dev.dimension.flare.di.koinInject

public class PodcastListPresenter(
    private val accountType: AccountType,
) : PresenterBase<PodcastListPresenter.State>() {
    private val accountService: AccountService by koinInject()
    private val serviceFlow by lazy {
        accountService.accountServiceFlow(accountType)
    }

    @Immutable
    public interface State {
        public val data: UiState<ImmutableList<UiPodcast>>
    }

    @Composable
    override fun body(): State {
        val service by serviceFlow.collectAsUiState()
        val podcasts =
            service.flatMap {
                require(it is XQTDataSource)
                remember(it) {
                    flow {
                        emit(UiState.Loading())
                        it
                            .getFleets()
                            .fold(
                                onSuccess = {
                                    emit(UiState.Success(it))
                                },
                                onFailure = {
                                    emit(UiState.Error(it))
                                },
                            )
                    }
                }.collectAsState(UiState.Loading()).value
            }
        return object : State {
            override val data = podcasts
        }
    }
}
