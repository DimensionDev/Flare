package dev.dimension.flare.ui.presenter.podcast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.data.datasource.xqt.XQTDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiPodcast
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class PodcastPresenter(
    private val accountType: AccountType,
    private val id: String,
) : PresenterBase<PodcastPresenter.State>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    public interface State {
        public val data: UiState<UiPodcast>
    }

    @Composable
    override fun body(): State {
        val service = accountServiceProvider(accountType, accountRepository)
        val result =
            service
                .map {
                    remember<Flow<UiState<UiPodcast>>>(id) {
                        require(it is XQTDataSource)
                        flow {
                            val result =
                                it
                                    .podcast(id = id)
                                    .fold(
                                        onSuccess = { UiState.Success(it) },
                                        onFailure = { UiState.Error(it) },
                                    )
                            emit(result)
                        }
                    }
                }.flatMap {
                    it.collectAsUiState().value.flatMap { it }
                }
        return object : State {
            override val data: UiState<UiPodcast> = result
        }
    }
}
