package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Check
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.OutlinedTextField2
import dev.dimension.flare.ui.model.UiKeywordFilter
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.LocalFilterPresenter
import moe.tlaster.precompose.molecule.producePresenter

// @Destination<RootGraph>(
//    style = FullScreenDialogStyle::class,
//    wrappers = [ThemeWrapper::class],
// )
// @Composable
// internal fun LocalFilterEditDialogRoute(
//    navigator: DestinationsNavigator,
//    keyword: String?,
// ) {
//    LocalFilterEditDialog(
//        keyword = keyword,
//        onBack = navigator::navigateUp,
//    )
// }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun LocalFilterEditDialog(
    keyword: String?,
    onBack: () -> Unit,
) {
    val state by producePresenter {
        presenter(keyword = keyword)
    }
    FlareScaffold(
        topBar = {
            FlareTopAppBar(
                title = {
                    Text(
                        text =
                            stringResource(
                                id =
                                    if (keyword == null) {
                                        R.string.local_filter_add_title
                                    } else {
                                        R.string.local_filter_edit_title
                                    },
                            ),
                    )
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                actions = {
                    IconButton(onClick = {
                        state.save()
                        onBack()
                    }) {
                        FAIcon(
                            FontAwesomeIcons.Solid.Check,
                            contentDescription = stringResource(id = android.R.string.ok),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .padding(contentPadding),
        ) {
            ListItem(
                headlineContent = {
                    OutlinedTextField2(
                        state = state.input,
                        placeholder = {
                            Text(text = stringResource(id = R.string.local_filter_keyword_hint))
                        },
                        label = {
                            Text(text = stringResource(id = R.string.local_filter_keyword))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
            ListItem(
                headlineContent = {
                    Text(text = stringResource(id = R.string.local_filter_for_timeline))
                },
                trailingContent = {
                    Switch(
                        checked = state.forTimeline,
                        onCheckedChange = state::setForTimeline,
                    )
                },
                modifier =
                    Modifier.clickable {
                        state.setForTimeline(!state.forTimeline)
                    },
            )
            ListItem(
                headlineContent = {
                    Text(text = stringResource(id = R.string.local_filter_for_notification))
                },
                trailingContent = {
                    Switch(
                        checked = state.forNotification,
                        onCheckedChange = state::setForNotification,
                    )
                },
                modifier =
                    Modifier.clickable {
                        state.setForNotification(!state.forNotification)
                    },
            )
            ListItem(
                headlineContent = {
                    Text(text = stringResource(id = R.string.local_filter_for_search))
                },
                trailingContent = {
                    Switch(
                        checked = state.forSearch,
                        onCheckedChange = state::setForSearch,
                    )
                },
                modifier =
                    Modifier.clickable {
                        state.setForSearch(!state.forSearch)
                    },
            )
            if (keyword != null) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(id = R.string.local_filter_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    trailingContent = {
                        FAIcon(
                            FontAwesomeIcons.Solid.Trash,
                            contentDescription = stringResource(id = R.string.delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            state.delete()
                            onBack()
                        },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun presenter(keyword: String?) =
    run {
        val input = rememberTextFieldState(keyword ?: "")
        var forTimeline by remember { mutableStateOf(true) }
        var forNotification by remember { mutableStateOf(true) }
        var forSearch by remember { mutableStateOf(true) }
//    var expiredAt by remember { mutableStateOf<Instant?>(null) }
        val state =
            remember {
                LocalFilterPresenter()
            }.invoke()

        state.items.onSuccess {
            LaunchedEffect(Unit) {
                it.toImmutableList().find { it.keyword == keyword }?.let { item ->
                    forTimeline = item.forTimeline
                    forNotification = item.forNotification
                    forSearch = item.forSearch
//                expiredAt = item.expiredAt
                }
            }
        }

        object {
            val input = input
            val forTimeline = forTimeline
            val forNotification = forNotification
            val forSearch = forSearch

            fun setForTimeline(value: Boolean) {
                forTimeline = value
            }

            fun setForNotification(value: Boolean) {
                forNotification = value
            }

            fun setForSearch(value: Boolean) {
                forSearch = value
            }

            fun save() {
                state.add(
                    item =
                        UiKeywordFilter(
                            keyword = input.text.toString(),
                            forTimeline = forTimeline,
                            forNotification = forNotification,
                            forSearch = forSearch,
                            expiredAt = null,
                        ),
                )
            }

            fun delete() {
                if (keyword != null) {
                    state.delete(keyword)
                }
            }
        }
    }
