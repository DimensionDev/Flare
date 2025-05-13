package dev.dimension.flare.ui.presenter.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.repository.flareDataSource
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class AiTLDRPresenter(
    private val source: String,
    private val targetLanguage: String = Locale.language,
) : PresenterBase<AiTLDRPresenter.State>(),
    KoinComponent {
    private val appDataStore by inject<AppDataStore>()

    public interface State {
        public val result: UiState<String>
    }

    @Composable
    override fun body(): State {
        val dataSource = flareDataSource(appDataStore)
        val result =
            remember {
                dataSource.map {
                    flow {
                        emit(UiState.Loading())
                        tryRun {
                            it.tldr(source, targetLanguage)
                        }.fold(
                            onSuccess = { response ->
                                emit(UiState.Success(response))
                            },
                            onFailure = { error ->
                                emit(UiState.Error(error))
                            },
                        )
                    }
                }
            }.flatMap { it.collectAsState(UiState.Loading()).value }
        return object : State {
            override val result: UiState<String> = result
        }
    }
}
