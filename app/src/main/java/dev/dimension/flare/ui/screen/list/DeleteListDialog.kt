package dev.dimension.flare.ui.screen.list

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.list.DeleteListPresenter
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

// @Destination<RootGraph>(
//    wrappers = [ThemeWrapper::class],
//    style = DestinationStyle.Dialog::class,
// )
// @Composable
// internal fun DeleteListRoute(
//    navigator: DestinationsNavigator,
//    accountType: AccountType,
//    listId: String,
//    title: String?,
// ) {
//    DeleteListDialog(
//        accountType,
//        listId = listId,
//        title = title,
//        onDismissRequest = navigator::navigateUp,
//    )
// }

@Composable
internal fun DeleteListDialog(
    accountType: AccountType,
    listId: String,
    title: String?,
    onDismissRequest: () -> Unit,
) {
    val state by producePresenter {
        presenter(accountType, listId, onDismissRequest)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    state.confirm()
                },
                enabled = !state.isLoading,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(
                    text = stringResource(id = android.R.string.ok),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = !state.isLoading,
            ) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        },
        text = {
            Text(text = stringResource(id = R.string.list_delete_confirm, title ?: ""))
        },
        title = {
            Text(text = stringResource(id = R.string.list_delete))
        },
    )
}

@Composable
private fun presenter(
    accountType: AccountType,
    listId: String,
    onBack: () -> Unit,
) = run {
    val scope = rememberCoroutineScope()
    val presenter =
        remember {
            DeleteListPresenter(accountType, listId)
        }.invoke()
    var isLoading by remember {
        mutableStateOf(false)
    }
    object {
        val isLoading = isLoading

        fun confirm() {
            scope.launch {
                isLoading = true
                presenter.deleteList()
                isLoading = false
                onBack.invoke()
            }
        }
    }
}
