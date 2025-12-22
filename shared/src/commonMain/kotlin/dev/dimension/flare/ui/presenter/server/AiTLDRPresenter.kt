package dev.dimension.flare.ui.presenter.server

import androidx.compose.runtime.Composable
import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.datasource.flare.FlareDataSource
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class AiTLDRPresenter(
    private val source: String,
    private val targetLanguage: String = Locale.language,
) : PresenterBase<UiState<String>>(),
    KoinComponent {
    private val appDataStore by inject<AppDataStore>()

    private val dataFlow by lazy {
        appDataStore.flareDataStore.data
            .map {
                FlareDataSource(it.serverUrl)
            }.map {
                it.tldr(source, targetLanguage)
            }
    }

    @Composable
    override fun body(): UiState<String> = dataFlow.collectAsUiState().value
}
