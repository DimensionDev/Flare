package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.presenter.home.HomeTimelinePresenter
import dev.dimension.flare.ui.presenter.invoke
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun HomeTimelineScreen() {
    val state by producePresenter {
        presenter()
    }
    Box(
        modifier =
            Modifier
                .fillMaxSize(),
    ) {
        LazyStatusVerticalStaggeredGrid(
            contentPadding = PaddingValues(16.dp),
        ) {
            status(state.listState)
        }
    }
}

@Composable
private fun presenter() =
    run {
        remember { HomeTimelinePresenter(dev.dimension.flare.model.AccountType.Guest) }.invoke()
    }
