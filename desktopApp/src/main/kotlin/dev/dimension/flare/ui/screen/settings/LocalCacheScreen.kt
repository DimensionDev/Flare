package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.common.isEmpty
import dev.dimension.flare.common.isSuccess
import dev.dimension.flare.local_history_search_status_title
import dev.dimension.flare.local_history_search_user_title
import dev.dimension.flare.ui.common.itemsIndexed
import dev.dimension.flare.ui.component.AccountItem
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.LocalCacheSearchPresenter
import io.github.composefluent.component.LiteFilter
import io.github.composefluent.component.PillButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun LocalCacheScreen() {
    val uriHandler = LocalUriHandler.current
    val state by producePresenter {
        presenter()
    }
    val lazyListState = rememberLazyStaggeredGridState()
    LazyStatusVerticalStaggeredGrid(
        state = lazyListState,
        contentPadding = LocalWindowPadding.current,
        modifier =
            Modifier
                .fillMaxSize(),
    ) {
        item(
            span = StaggeredGridItemSpan.FullLine,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize(),
            ) {
                TextField(
                    state = state.searchTextState,
                    modifier =
                        Modifier
                            .widthIn(min = 200.dp)
                            .align(Alignment.CenterEnd),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
            }
        }
        item(
            span = StaggeredGridItemSpan.FullLine,
        ) {
            LiteFilter {
                state.allSearchTypes.forEach { searchType ->
                    PillButton(
                        selected = state.selectedSearchType == searchType,
                        onSelectedChanged = {
                            if (it) {
                                state.setSearchType(searchType)
                            }
                        },
                    ) {
                        Text(stringResource(searchType.title))
                    }
                }
            }
        }
        when (state.selectedSearchType) {
            SearchType.Status ->
                status(
                    if (state.data.isEmpty || !state.data.isSuccess()) {
                        state.history
                    } else {
                        state.data
                    },
                )
            SearchType.User ->
                itemsIndexed(
                    if (state.searchUser.isEmpty || !state.searchUser.isSuccess()) {
                        state.userHistory
                    } else {
                        state.searchUser
                    },
                ) { index, itemsCount, user ->
                    AccountItem(
                        userState = UiState.Success(user),
                        onClick = { user.onClicked.invoke(ClickContext(uriHandler::openUri)) },
                        toLogin = {},
                    )
                }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun presenter() =
    run {
        val searchTextState = rememberTextFieldState()
        val state = remember { LocalCacheSearchPresenter() }.invoke()
        var selectedSearchType by remember { mutableStateOf(SearchType.Status) }
        LaunchedEffect(Unit) {
            snapshotFlow { searchTextState.text }
                .distinctUntilChanged()
                .debounce(1.seconds)
                .collect {
                    state.setQuery(it.toString())
                }
        }
        object : LocalCacheSearchPresenter.State by state {
            val searchTextState = searchTextState

            val selectedSearchType = selectedSearchType
            val allSearchTypes = SearchType.entries.toImmutableList()

            fun setSearchType(value: SearchType) {
                selectedSearchType = value
            }
        }
    }

private enum class SearchType(
    val title: StringResource,
) {
    Status(Res.string.local_history_search_status_title),
    User(Res.string.local_history_search_user_title),
}
