package dev.dimension.flare.ui.screen.mastodon

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.spec.DestinationStyle
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.OutlinedTextField2
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.presenter.home.mastodon.CreateListPresenter
import dev.dimension.flare.ui.presenter.invoke
import kotlinx.coroutines.launch

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
    style = DestinationStyle.Dialog::class,
)
@Composable
internal fun CreateListRoute(
    navigator: DestinationsNavigator,
    accountType: AccountType,
) {
    CreateListDialog(accountType, onDismissRequest = navigator::navigateUp)
}

@Composable
private fun CreateListDialog(
    accountType: AccountType,
    onDismissRequest: () -> Unit,
) {
    val state by producePresenter {
        presenter(accountType, onDismissRequest)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = state.canConfirm,
                onClick = {
                    state.confirm()
                },
            ) {
                Text(text = stringResource(id = android.R.string.ok))
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
            OutlinedTextField2(
                state = state.text,
                label = { Text(text = stringResource(id = R.string.list_create_name)) },
                placeholder = { Text(text = stringResource(id = R.string.list_create_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
            )
        },
        title = {
            Text(text = stringResource(id = R.string.list_create))
        },
    )
}

@Composable
private fun presenter(
    accountType: AccountType,
    onBack: () -> Unit,
) = run {
    val scope = rememberCoroutineScope()
    val presenter =
        remember {
            CreateListPresenter(accountType)
        }.invoke()
    var isLoading by remember {
        mutableStateOf(false)
    }
    val textState = rememberTextFieldState()

    object {
        val text = textState
        val canConfirm = textState.text.isNotEmpty() && !isLoading
        val isLoading = isLoading

        fun confirm() {
            scope.launch {
                isLoading = true
                presenter.createList(text.text.toString())
                isLoading = false
                onBack.invoke()
            }
        }
    }
}
