package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Xmark
import dev.dimension.flare.R
import dev.dimension.flare.ui.common.items
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.AdaptiveCard
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.LocalCacheSearchPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.toImmutableList
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
    val uriHandler = LocalUriHandler.current
    FlareScaffold(
        topBar = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.SearchBar(
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
                                Text(stringResource(R.string.local_history_search_placeholder))
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
                            trailingIcon = {
                                if (text.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            text = ""
                                        },
                                    ) {
                                        FAIcon(
                                            FontAwesomeIcons.Solid.Xmark,
                                            contentDescription = null,
                                        )
                                    }
                                }
                            },
                        )
                    },
                    content = {
                    },
                    expanded = state.searchBarExpanded,
                    onExpandedChange = state::setSearchBarExpanded,
                )
            }
        },
    ) { contentPadding ->
        LazyStatusVerticalStaggeredGrid(
            state = lazyListState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(
                span = StaggeredGridItemSpan.FullLine,
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier =
                        Modifier
                            .padding(horizontal = screenHorizontalPadding, vertical = 8.dp)
                            .widthIn(max = 300.dp),
                ) {
                    state.allSearchTypes.forEachIndexed { index, searchType ->
                        SegmentedButton(
                            selected = state.selectedSearchType == searchType,
                            onClick = {
                                state.setSearchType(searchType)
                            },
                            shape =
                                SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = state.allSearchTypes.size,
                                ),
                        ) {
                            Text(text = stringResource(id = searchType.title))
                        }
                    }
                }
            }
            when (state.selectedSearchType) {
                SearchType.Status ->
                    status(
                        if (text.isEmpty()) {
                            state.history
                        } else {
                            state.data
                        },
                    )
                SearchType.User ->
                    items(
                        if (text.isEmpty()) {
                            state.userHistory
                        } else {
                            state.searchUser
                        },
                    ) { user ->
                        AdaptiveCard(
                            content = {
                                AccountItem(
                                    userState = UiState.Success(user),
                                    onClick = { user.onClicked.invoke(ClickContext(uriHandler::openUri)) },
                                    toLogin = {},
                                )
                            },
                        )
                    }
            }
        }
    }
}

@Composable
private fun presenter() =
    run {
        val state = remember { LocalCacheSearchPresenter() }.invoke()
        var searchBarExpanded by remember { mutableStateOf(false) }
        var selectedSearchType by remember { mutableStateOf(SearchType.Status) }
        object : LocalCacheSearchPresenter.State by state {
            val searchBarExpanded = searchBarExpanded

            fun setSearchBarExpanded(value: Boolean) {
                searchBarExpanded = value
            }

            val selectedSearchType = selectedSearchType
            val allSearchTypes = SearchType.entries.toImmutableList()

            fun setSearchType(value: SearchType) {
                selectedSearchType = value
            }
        }
    }

private enum class SearchType(
    val title: Int,
) {
    Status(R.string.local_history_search_status_title),
    User(R.string.local_history_search_user_title),
}
