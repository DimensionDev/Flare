package dev.dimension.flare.ui.presenter.podcast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import dev.dimension.flare.data.datasource.xqt.XQTDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiPodcast
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class PodcastListPresenter(
    private val accountType: AccountType,
) : PresenterBase<PodcastListPresenter.State>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    public interface State {
        public val data: UiState<ImmutableList<UiPodcast>>
    }

    @Composable
    override fun body(): State {
        val service = accountServiceProvider(accountType, accountRepository)
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
