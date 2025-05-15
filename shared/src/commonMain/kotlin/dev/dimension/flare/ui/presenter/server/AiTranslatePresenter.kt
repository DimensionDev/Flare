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
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class AiTranslatePresenter(
    private val source: String,
    private val targetLanguage: String = Locale.language,
) : PresenterBase<UiState<String>>(),
    KoinComponent {
    private val appDataStore by inject<AppDataStore>()

    @Composable
    override fun body(): UiState<String> {
        val dataSource = flareDataSource(appDataStore)
        return dataSource.flatMap {
            remember<Flow<UiState<String>>>(it, source, targetLanguage) {
                flow<UiState<String>> {
                    emit(UiState.Loading())
                    tryRun {
                        it.translate(source, targetLanguage)
                    }.fold(
                        onSuccess = { response ->
                            emit(UiState.Success(response))
                        },
                        onFailure = { error ->
                            emit(UiState.Error(error))
                        },
                    )
                }
            }.collectAsState(UiState.Loading()).value
        }
    }
}
