package dev.dimension.flare.ui.screen.status.action

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.annotation.parameters.DeepLink
import com.ramcosta.composedestinations.annotation.parameters.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.spec.DestinationStyle
import dev.dimension.flare.R
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.action.DeleteStatusPresenter
import dev.dimension.flare.ui.presenter.status.action.DeleteStatusState

@Composable
@Destination<RootGraph>(
    style = DestinationStyle.Dialog::class,
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
        DeepLink(
            uriPattern = AppDeepLink.DeleteStatus.ROUTE,
        ),
    ],
    wrappers = [ThemeWrapper::class],
)
internal fun DeleteStatusConfirmRoute(
    navigator: DestinationsNavigator,
    accountKey: MicroBlogKey,
    statusKey: MicroBlogKey,
) {
    DeleteStatusConfirmDialog(
        statusKey = statusKey,
        onBack = {
            navigator.navigateUp()
        },
        accountType = AccountType.Specific(accountKey),
    )
}

@Composable
private fun DeleteStatusConfirmDialog(
    statusKey: MicroBlogKey,
    accountType: AccountType,
    onBack: () -> Unit,
) {
    val state by producePresenter(key = "DeleteStatusPresenter_${accountType}_$statusKey") {
        deleteStatusConfirmPresenter(
            statusKey = statusKey,
            accountType = accountType,
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
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) {
                Text(text = stringResource(id = android.R.string.cancel))
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
private fun deleteStatusConfirmPresenter(
    statusKey: MicroBlogKey,
    accountType: AccountType,
) = run {
    val state =
        remember(accountType, statusKey) {
            DeleteStatusPresenter(
                accountType = accountType,
                statusKey = statusKey,
            )
        }.invoke()

    object : DeleteStatusState by state {
    }
}
