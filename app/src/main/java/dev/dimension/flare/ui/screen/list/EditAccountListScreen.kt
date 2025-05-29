package dev.dimension.flare.ui.screen.list

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.uiListItemComponent
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.list.EditAccountListPresenter
import io.github.fornewid.placeholder.material3.placeholder
import moe.tlaster.precompose.molecule.producePresenter

// @Destination<RootGraph>
// @Composable
// internal fun EditAccountListRoute(
//    navigator: DestinationsNavigator,
//    accountType: AccountType,
//    userKey: MicroBlogKey,
// ) {
//    EditAccountListScreen(
//        accountType = accountType,
//        userKey = userKey,
//        onBack = navigator::navigateUp,
//    )
// }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditAccountListScreen(
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
            FlareTopAppBar(
                title = {
                    Text(stringResource(R.string.edit_account_list_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
            )
        },
        content = { contentPadding ->
            LazyColumn(
                contentPadding = contentPadding,
            ) {
                uiListItemComponent(
                    state.lists,
                ) { item ->
                    state.userLists
                        .onSuccess {
                            if (it.any { list -> list.id == item.id }) {
                                IconButton(
                                    onClick = { state.removeList(item) },
                                ) {
                                    FAIcon(
                                        FontAwesomeIcons.Solid.Trash,
                                        contentDescription = stringResource(id = R.string.edit_list_member_remove),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = { state.addList(item) },
                                ) {
                                    FAIcon(
                                        FontAwesomeIcons.Solid.Plus,
                                        contentDescription = stringResource(id = R.string.edit_list_member_add),
                                    )
                                }
                            }
                        }.onLoading {
                            IconButton(
                                onClick = {
                                },
                            ) {
                                FAIcon(
                                    FontAwesomeIcons.Solid.Plus,
                                    contentDescription = stringResource(id = R.string.edit_list_member_add),
                                    modifier = Modifier.placeholder(true),
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
    userKey: MicroBlogKey,
) = run {
    remember("EditAccountListPresenter_${accountType}_$userKey") {
        EditAccountListPresenter(
            accountType = accountType,
            userKey = userKey,
        )
    }.invoke()
}
