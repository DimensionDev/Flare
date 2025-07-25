package dev.dimension.flare.ui.screen.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.MagnifyingGlass
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.common.onEmpty
import dev.dimension.flare.common.onError
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.list.EditListMemberPresenter
import dev.dimension.flare.ui.presenter.list.EditListMemberState
import dev.dimension.flare.ui.presenter.list.EmptyQueryException
import dev.dimension.flare.ui.screen.settings.AccountItem
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun EditListMemberScreen(
    accountType: AccountType,
    listId: String,
    onBack: () -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter {
        presenter(accountType, listId)
    }
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.edit_list_member_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        content = { contentPadding ->
            LazyColumn(
                contentPadding = contentPadding,
                modifier =
                    Modifier
                        .padding(horizontal = screenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                stickyHeader {
                    OutlinedTextField(
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
                }
                item {
                    Spacer(modifier = Modifier.height(12.dp))
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
                                modifier =
                                    Modifier
                                        .listCard(
                                            index = index,
                                            totalCount = itemCount,
                                        ),
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
                            AccountItem(
                                userState = UiState.Loading(),
                                onClick = {},
                                toLogin = { },
                                modifier =
                                    Modifier
                                        .listCard(
                                            index = it,
                                            totalCount = 10,
                                        ),
                            )
                        }
                    }.onEmpty {
                        item {
                            ListItem(
                                headlineContent = {
                                    Text(text = stringResource(id = R.string.edit_list_member_search_empty))
                                },
                                modifier =
                                    Modifier
                                        .listCard(),
                            )
                        }
                    }.onError {
                        if (it is EmptyQueryException) {
                            item {
                                ListItem(
                                    headlineContent = {
                                        Text(text = stringResource(id = R.string.edit_list_member_search_empty))
                                    },
                                    modifier =
                                        Modifier
                                            .listCard(),
                                )
                            }
                        } else {
                            item {
                                ListItem(
                                    headlineContent = {
                                        Text(text = stringResource(id = R.string.edit_list_member_search_error))
                                    },
                                    modifier =
                                        Modifier
                                            .listCard(),
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
