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
import dev.dimension.flare.ui.presenter.status.action.MastodonReportPresenter

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
fun MastodonReportRoute(
    navigator: DestinationsNavigator,
    userKey: MicroBlogKey,
    statusKey: MicroBlogKey?,
    accountData: AccountData,
) {
    MastodonReportDialog(
        statusKey = statusKey,
        userKey = userKey,
        onBack = {
            navigator.navigateUp()
        },
        accountData = accountData,
    )
}

@Composable
fun MastodonReportDialog(
    userKey: MicroBlogKey,
    statusKey: MicroBlogKey?,
    accountData: AccountData,
    onBack: () -> Unit,
) {
    val state by producePresenter("${userKey}_${accountData.data}_$statusKey") {
        mastodonReportPresenter(
            userKey = userKey,
            statusKey = statusKey,
            accountData = accountData,
        )
    }

    AlertDialog(
        onDismissRequest = onBack,
        confirmButton = {
            TextButton(
                onClick = {
                    state.report()
                    onBack.invoke()
                },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onBack,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
        title = {
            Text(stringResource(R.string.mastodon_report_title))
        },
        text = {
            Text(stringResource(R.string.mastodon_report_description))
        },
    )
}

@Composable
private fun mastodonReportPresenter(
    userKey: MicroBlogKey,
    statusKey: MicroBlogKey?,
    accountData: AccountData,
) = run {
    val state =
        remember(userKey, statusKey, accountData.data) {
            MastodonReportPresenter(
                accountKey = accountData.data,
                userKey = userKey,
                statusKey = statusKey,
            )
        }.invoke()
    state
}
