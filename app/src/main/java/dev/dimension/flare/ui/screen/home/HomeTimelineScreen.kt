package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import dev.dimension.flare.R
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.HomeTimelinePresenter
import kotlinx.coroutines.launch
import org.koin.compose.rememberKoinInject

@RootNavGraph(start = true) // sets this as the start destination of the default nav graph
@Destination(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun HomeRoute() {
    HomeTimelineScreen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeTimelineScreen() {
    val state by producePresenter {
        homeTimelinePresenter()
    }
    val lazyListState = rememberLazyListState()

    val isAtTheTop by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0
        }
    }
    LaunchedEffect(isAtTheTop) {
        if (isAtTheTop) {
            state.state.onNewTootsShown()
        }
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.home_tab_home_title))
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        RefreshContainer(
            modifier =
                Modifier
                    .fillMaxSize(),
            onRefresh = state.state::refresh,
            indicatorPadding = contentPadding,
            content = {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    with(state.state.listState) {
                        with(state.statusEvent) {
                            status()
                        }
                    }
                }
                state.state.listState.onSuccess {
                    AnimatedVisibility(
                        state.state.showNewToots,
                        enter = slideInVertically { -it },
                        exit = slideOutVertically { -it },
                        modifier = Modifier.align(Alignment.TopCenter),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                state.state.onNewTootsShown()
                                scope.launch {
                                    lazyListState.animateScrollToItem(0)
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = stringResource(id = R.string.home_timeline_new_toots))
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun homeTimelinePresenter(statusEvent: StatusEvent = rememberKoinInject()) =
    run {
        val state = remember { HomeTimelinePresenter() }.invoke()
        object {
            val state = state
            val statusEvent = statusEvent
        }
    }
