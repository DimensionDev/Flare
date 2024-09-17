package dev.dimension.flare.ui.screen.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ArrowLeft
import compose.icons.fontawesomeicons.solid.MagnifyingGlass
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.common.onEmpty
import dev.dimension.flare.common.onError
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.OutlinedTextField2
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.list.EditListMemberPresenter
import dev.dimension.flare.ui.presenter.list.EditListMemberState
import dev.dimension.flare.ui.presenter.list.EmptyQueryException
import dev.dimension.flare.ui.screen.settings.AccountItem

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun EditListMemberRoute(
    navigator: DestinationsNavigator,
    accountType: AccountType,
    listId: String,
) {
    EditListMemberScreen(
        accountType = accountType,
        listId = listId,
        onBack = navigator::navigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun EditListMemberScreen(
    accountType: AccountType,
    listId: String,
    onBack: () -> Unit,
) {
    val state by producePresenter {
        presenter(accountType, listId)
    }

    FlareScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.edit_list_member_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        FAIcon(
                            FontAwesomeIcons.Solid.ArrowLeft,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
        content = { contentPadding ->
            LazyColumn(
                contentPadding = contentPadding,
            ) {
                stickyHeader {
                    ListItem(
                        headlineContent = {
                            OutlinedTextField2(
                                state = state.text,
                                placeholder = {
                                    Text(stringResource(R.string.edit_list_member_placeholder))
                                },
                                label = {
                                    Text(stringResource(R.string.edit_list_member_label))
                                },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        state.setFilter(state.text.text.toString())
                                    }) {
                                        FAIcon(
                                            FontAwesomeIcons.Solid.MagnifyingGlass,
                                            contentDescription = stringResource(R.string.edit_list_member_search),
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions =
                                    KeyboardOptions(
                                        imeAction = ImeAction.Search,
                                    ),
                                onKeyboardAction = {
                                    state.setFilter(state.text.text.toString())
                                },
                                lineLimits = TextFieldLineLimits.SingleLine,
                            )
                        },
                    )
                }
                item {
                    HorizontalDivider()
                }
                state.users
                    .onSuccess {
                        items(itemCount) { index ->
                            val item = get(index)
                            val userState =
                                item?.first?.let {
                                    UiState.Success(it)
                                } ?: UiState.Loading()
                            AccountItem(
                                userState = userState,
                                onClick = { },
                                toLogin = { },
                                trailingContent = {
                                    val isMember = item?.second
                                    if (isMember != null) {
                                        IconButton(onClick = {
                                            if (isMember) {
                                                state.removeMember(item.first.key)
                                            } else {
                                                state.addMember(item.first.key)
                                            }
                                        }) {
                                            if (isMember) {
                                                FAIcon(
                                                    FontAwesomeIcons.Solid.Trash,
                                                    contentDescription = stringResource(id = R.string.edit_list_member_remove),
                                                    tint = MaterialTheme.colorScheme.error,
                                                )
                                            } else {
                                                FAIcon(
                                                    FontAwesomeIcons.Solid.Plus,
                                                    contentDescription = stringResource(id = R.string.edit_list_member_add),
                                                )
                                            }
                                        }
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
                            ListItem(
                                headlineContent = {
                                    Text(text = stringResource(id = R.string.edit_list_member_search_empty))
                                },
                            )
                        }
                    }.onError {
                        if (it is EmptyQueryException) {
                            item {
                                ListItem(
                                    headlineContent = {
                                        Text(text = stringResource(id = R.string.edit_list_member_search_empty))
                                    },
                                )
                            }
                        } else {
                            item {
                                ListItem(
                                    headlineContent = {
                                        Text(text = stringResource(id = R.string.edit_list_member_search_error))
                                    },
                                )
                            }
                        }
                    }
            }
        },
    )
}

@Composable
private fun presenter(
    accountType: AccountType,
    listId: String,
) = run {
    val text = rememberTextFieldState()

    val state =
        remember(accountType, listId) {
            EditListMemberPresenter(accountType, listId)
        }.invoke()

    object : EditListMemberState by state {
        val text = text
    }
}
