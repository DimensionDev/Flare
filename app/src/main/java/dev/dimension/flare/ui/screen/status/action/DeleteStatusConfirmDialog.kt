package dev.dimension.flare.ui.screen.status.action

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.ramcosta.composedestinations.annotation.DeepLink
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.spec.DestinationStyle
import dev.dimension.flare.R
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.model.AccountData
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.action.DeleteStatusPresenter
import dev.dimension.flare.ui.presenter.status.action.DeleteStatusState

@Composable
@Destination(
    style = DestinationStyle.Dialog::class,
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
    ],
    wrappers = [ThemeWrapper::class],
)
fun DeleteStatusConfirmRoute(
    navigator: DestinationsNavigator,
    accountData: AccountData,
    statusKey: MicroBlogKey,
) {
    DeleteStatusConfirmDialog(
        statusKey = statusKey,
        onBack = {
            navigator.navigateUp()
        },
        accountData = accountData,
    )
}

@Composable
fun DeleteStatusConfirmDialog(
    statusKey: MicroBlogKey,
    accountData: AccountData,
    onBack: () -> Unit,
) {
    val state by producePresenter(key = "DeleteStatusPresenter_${accountData.data}_$statusKey") {
        deleteStatusConfirmPresenter(
            statusKey = statusKey,
            accountData = accountData,
        )
    }

    AlertDialog(
        onDismissRequest = onBack,
        confirmButton = {
            TextButton(
                onClick = {
                    state.delete()
                    onBack.invoke()
                },
            ) {
                Text(text = stringResource(id = R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) {
                Text(text = stringResource(id = R.string.cancel))
            }
        },
        title = {
            Text(text = stringResource(id = R.string.delete_status_title))
        },
        text = {
            Text(text = stringResource(id = R.string.delete_status_message))
        },
    )
}

@Composable
fun deleteStatusConfirmPresenter(
    statusKey: MicroBlogKey,
    accountData: AccountData,
) = run {
    val state =
        remember(accountData.data, statusKey) {
            DeleteStatusPresenter(
                accountKey = accountData.data,
                statusKey = statusKey,
            )
        }.invoke()

    object : DeleteStatusState by state {
    }
}
