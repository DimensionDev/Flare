package dev.dimension.flare.ui.screen.mastodon

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
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

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun EditListRoute(
    navigator: DestinationsNavigator,
    accountType: AccountType,
    listId: String,
) {
    EditListScreen(accountType, listId, onBack = navigator::navigateUp)
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
) {
    val state by producePresenter {
        presenter(accountType, listId)
    }
    FlareScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.list_edit))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = state::confirm) {
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
                        }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(id = R.string.list_edit_add_members),
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
                        AccountItem(userState = userState, onClick = {}, toLogin = { })
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
) = run {
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

    object : EditListState by state {
        val text = text

        fun confirm() {
        }
    }
}
