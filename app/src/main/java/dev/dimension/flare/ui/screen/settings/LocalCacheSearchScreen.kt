package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.LocalCacheSearchPresenter
import moe.tlaster.precompose.molecule.producePresenter

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun LocalCacheSearchRoute(navigator: ProxyDestinationsNavigator) {
    CompositionLocalProvider(
        LocalUriHandler provides navigator.uriHandler,
    ) {
        LocalCacheSearchScreen(
            onBack = navigator::navigateUp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalCacheSearchScreen(onBack: () -> Unit) {
    val state by producePresenter {
        presenter()
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    var text by rememberSaveable { mutableStateOf("") }
    val lazyListState = rememberLazyStaggeredGridState()
    FlareScaffold(
        topBar = {
            androidx.compose.material3.SearchBar(
                modifier = Modifier.fillMaxWidth(),
                inputField = {
                    SearchBarDefaults.InputField(
                        query = text,
                        onQueryChange = { text = it },
                        onSearch = {
                            state.setQuery(text)
                            state.setSearchBarExpanded(false)
                            keyboardController?.hide()
                        },
                        expanded = state.searchBarExpanded,
                        onExpandedChange = state::setSearchBarExpanded,
                        placeholder = {
                            Text("Hinted search text")
                        },
                        leadingIcon = {
                            BackButton(
                                onBack = {
                                    if (state.searchBarExpanded) {
                                        state.setSearchBarExpanded(false)
                                    } else {
                                        onBack()
                                    }
                                },
                            )
                        },
                    )
                },
                content = {
                },
                expanded = state.searchBarExpanded,
                onExpandedChange = state::setSearchBarExpanded,
            )
        },
    ) { contentPadding ->
        LazyStatusVerticalStaggeredGrid(
            state = lazyListState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            status(state.data)
        }
    }
}

@Composable
private fun presenter() =
    run {
        val state = remember { LocalCacheSearchPresenter() }.invoke()
        var searchBarExpanded by remember { mutableStateOf(false) }
        object : LocalCacheSearchPresenter.State by state {
            val searchBarExpanded = searchBarExpanded

            fun setSearchBarExpanded(value: Boolean) {
                searchBarExpanded = value
            }
        }
    }
