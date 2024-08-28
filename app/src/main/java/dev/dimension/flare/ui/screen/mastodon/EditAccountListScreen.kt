package dev.dimension.flare.ui.screen.mastodon

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import com.eygraber.compose.placeholder.material3.placeholder
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.mastodon.EditAccountListPresenter
import dev.dimension.flare.ui.presenter.invoke

@Destination<RootGraph>
@Composable
internal fun EditAccountListRoute(
    navigator: DestinationsNavigator,
    accountType: AccountType,
    userKey: MicroBlogKey,
) {
    EditAccountListScreen(
        accountType = accountType,
        userKey = userKey,
        onBack = navigator::navigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAccountListScreen(
    accountType: AccountType,
    userKey: MicroBlogKey,
    onBack: () -> Unit,
) {
    val state by producePresenter {
        presenter(
            accountType = accountType,
            userKey = userKey,
        )
    }
    FlareScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.edit_account_list_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
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
                state.lists
                    .onSuccess { lists ->
                        if (lists.isNotEmpty()) {
                            items(lists.size) { index ->
                                val key = lists.keys.elementAt(index)
                                val value = lists[key] ?: false
                                ListItem(
                                    headlineContent = {
                                        Text(text = key.title)
                                    },
                                    trailingContent = {
                                        if (value) {
                                            IconButton(
                                                onClick = { state.removeList(key) },
                                            ) {
                                                Icon(
                                                    Icons.Default.Remove,
                                                    contentDescription = stringResource(id = R.string.edit_list_member_remove),
                                                    tint = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        } else {
                                            IconButton(
                                                onClick = { state.addList(key) },
                                            ) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = stringResource(id = R.string.edit_list_member_add),
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        } else {
                            item {
                                ListItem(
                                    headlineContent = {
                                        Text(text = stringResource(id = R.string.edit_account_list_empty))
                                    },
                                )
                            }
                        }
                    }.onLoading {
                        items(10) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = "Loading...",
                                        modifier = Modifier.placeholder(true),
                                    )
                                },
                            )
                        }
                    }.onError {
                        item {
                            ListItem(
                                headlineContent = {
                                    Text(text = stringResource(id = R.string.edit_account_list_error))
                                },
                            )
                        }
                    }
            }
        },
    )
}

@Composable
private fun presenter(
    accountType: AccountType,
    userKey: MicroBlogKey,
) = run {
    remember("EditAccountListPresenter_${accountType}_$userKey") {
        EditAccountListPresenter(
            accountType = accountType,
            userKey = userKey,
        )
    }.invoke()
}
