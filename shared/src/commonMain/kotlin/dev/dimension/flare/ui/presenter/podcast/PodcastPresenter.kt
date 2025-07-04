package dev.dimension.flare.ui.presenter.podcast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.datasource.xqt.XQTDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiPodcast
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.map
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

    private val dataFlow by lazy {
        accountServiceFlow(
            accountType = accountType,
            repository = accountRepository,
        ).map {
            require(it is XQTDataSource)
            it.podcast(id).fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error<UiPodcast>(it) },
            )
        }
    }

    @Composable
    override fun body(): State {
        val result by dataFlow.flattenUiState()
        return object : State {
            override val data: UiState<UiPodcast> = result
        }
    }
}
