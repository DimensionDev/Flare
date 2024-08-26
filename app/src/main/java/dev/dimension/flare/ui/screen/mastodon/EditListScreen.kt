package dev.dimension.flare.ui.screen.mastodon

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.EditListMemberRouteDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.common.onEmpty
import dev.dimension.flare.common.onError
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.OutlinedTextField2
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.mastodon.EditListPresenter
import dev.dimension.flare.ui.presenter.home.mastodon.EditListState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.settings.AccountItem
import kotlinx.coroutines.launch

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun EditListRoute(
    navigator: DestinationsNavigator,
    accountType: AccountType,
    listId: String,
) {
    EditListScreen(
        accountType,
        listId,
        onBack = navigator::navigateUp,
        toEditUser = {
            navigator.navigate(EditListMemberRouteDestination(accountType, listId))
        },
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)
@Composable
private fun EditListScreen(
    accountType: AccountType,
    listId: String,
    onBack: () -> Unit,
    toEditUser: () -> Unit,
) {
    val state by producePresenter {
        presenter(accountType, listId, onBack)
    }
    FlareScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.list_edit))
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = state::confirm,
                        enabled = state.listInfo is UiState.Success && !state.isLoading,
                    ) {
                        Icon(
                            Icons.Default.Done,
                            contentDescription = stringResource(id = android.R.string.ok),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            contentPadding = contentPadding,
        ) {
            item {
                ListItem(
                    headlineContent = {
                        OutlinedTextField2(
                            state = state.text,
                            label = { Text(text = stringResource(id = R.string.list_create_name)) },
                            placeholder = { Text(text = stringResource(id = R.string.list_create_name_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading && state.listInfo is UiState.Success,
                        )
                    },
                )
            }
            stickyHeader {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(id = R.string.list_edit_members))
                    },
                    trailingContent = {
                        IconButton(onClick = {
                            toEditUser.invoke()
                        }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(id = R.string.list_edit_edit_members),
                            )
                        }
                    },
                )
            }
            state.memberInfo
                .onSuccess {
                    items(itemCount) { index ->
                        val item = get(index)
                        val userState =
                            item?.let {
                                UiState.Success(item)
                            } ?: UiState.Loading()
                        AccountItem(
                            userState = userState,
                            onClick = {},
                            toLogin = { },
                            trailingContent = {
                                var showMenu by remember {
                                    mutableStateOf(false)
                                }
                                IconButton(onClick = {
                                    showMenu = true
                                }) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = stringResource(id = R.string.more),
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = {
                                        showMenu = false
                                    },
                                ) {
                                    DropdownMenuItem(
                                        onClick = {
                                            showMenu = false
                                            if (item != null) {
                                                state.removeMember(item.key.id)
                                            }
                                        },
                                        text = {
                                            Text(
                                                text = stringResource(id = R.string.delete),
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(id = R.string.delete),
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                    )
                                }
                            },
                        )
                    }
                }.onLoading {
                    items(10) {
                        AccountItem(userState = UiState.Loading(), onClick = {}, toLogin = { })
                    }
                }.onEmpty {
                    item {
                        Text(text = stringResource(id = R.string.list_edit_no_members))
                    }
                }.onError {
                    item {
                        Text(text = stringResource(id = R.string.list_edit_members_error))
                    }
                }
        }
    }
}

@Composable
private fun presenter(
    accountType: AccountType,
    listId: String,
    onBack: () -> Unit,
) = run {
    val scope = rememberCoroutineScope()
    val state =
        remember(accountType, listId) {
            EditListPresenter(accountType, listId)
        }.invoke()
    val text = rememberTextFieldState()
    state.listInfo.onSuccess {
        LaunchedEffect(Unit) {
            text.edit {
                append(it.title)
            }
        }
    }
    var isLoading by remember {
        mutableStateOf(false)
    }

    object : EditListState by state {
        val text = text
        val isLoading = isLoading

        fun confirm() {
            scope.launch {
                isLoading = true
                state.updateTitle(text.text.toString())
                isLoading = false
                onBack.invoke()
            }
        }
    }
}
