package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.createSampleStatus
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter

class AppearancePresenter : PresenterBase<AppearanceState>() {
    @Composable
    override fun body(): AppearanceState {
        val account =
            remember {
                ActiveAccountPresenter()
            }.body()
        return object : AppearanceState {
            override val sampleStatus =
                account.user.map {
                    createSampleStatus(it)
                }
        }
    }
}

interface AppearanceState {
    val sampleStatus: UiState<UiTimeline>
}
