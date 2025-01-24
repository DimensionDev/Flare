package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbRssSources
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.model.takeSuccessOr
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

public class EditRssSourcePresenter(
    private val id: Int?,
) : PresenterBase<EditRssSourcePresenter.State>(),
    KoinComponent {
    private val appDatabase by inject<AppDatabase>()
    private val scope by inject<CoroutineScope>()

    public interface State : CheckRssSourcePresenter.State {
        public fun update(
            url: String,
            title: String,
        )

        public val canConfirm: Boolean
        public val data: UiState<UiRssSource>
    }

    @Composable
    override fun body(): State {
        val checkRssSourcePresenterState = remember { CheckRssSourcePresenter() }.body()
        val data by remember(id) {
            appDatabase
                .rssSourceDao()
                .get(id ?: -1)
                .map {
                    it.render()
                }
        }.collectAsUiState()
        return object : State, CheckRssSourcePresenter.State by checkRssSourcePresenterState {
            override fun update(
                url: String,
                title: String,
            ) {
                scope.launch {
                    appDatabase
                        .rssSourceDao()
                        .insert(DbRssSources(id = id ?: 0, url = url, title = title, lastUpdate = 0))
                }
            }

            override val canConfirm: Boolean
                get() = isValid.takeSuccessOr(false)
            override val data: UiState<UiRssSource>
                get() = data
        }
    }
}
