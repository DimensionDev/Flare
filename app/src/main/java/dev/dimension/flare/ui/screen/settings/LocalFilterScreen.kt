package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.LocalFilterEditDialogRouteDestination
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.Plus
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.LocalFilterPresenter
import dev.dimension.flare.ui.presenter.settings.LocalFilterState
import moe.tlaster.precompose.molecule.producePresenter

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun LocalFilterRoute(navigator: ProxyDestinationsNavigator) {
    LocalFilterScreen(
        onBack = navigator::navigateUp,
        edit = { keyword ->
            navigator.navigate(LocalFilterEditDialogRouteDestination(keyword))
        },
        add = {
            navigator.navigate(LocalFilterEditDialogRouteDestination(null))
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalFilterScreen(
    onBack: () -> Unit,
    edit: (String) -> Unit,
    add: () -> Unit,
) {
    val state by producePresenter {
        presenter()
    }

    FlareScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_local_filter_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                actions = {
                    IconButton(onClick = add) {
                        FAIcon(
                            FontAwesomeIcons.Solid.Plus,
                            contentDescription = stringResource(id = R.string.local_filter_add),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            contentPadding = contentPadding,
        ) {
            state.items.onSuccess { list ->
                items(list.size) { index ->
                    val item = list[index]
                    ListItem(
                        headlineContent = {
                            Text(text = item.keyword)
                        },
//                        supportingContent = {
//                            Text(text = item.humanizedExpiredAt ?: stringResource(id = R.string.local_filter_no_expiration))
//                        },
                        trailingContent = {
                            IconButton(onClick = {
                                edit(item.keyword)
                            }) {
                                FAIcon(
                                    FontAwesomeIcons.Solid.Pen,
                                    contentDescription = stringResource(id = R.string.local_filter_edit_title),
                                )
                            }
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
        val state =
            remember {
                LocalFilterPresenter()
            }.invoke()
        object : LocalFilterState by state {
        }
    }
