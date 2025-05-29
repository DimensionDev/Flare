package dev.dimension.flare.ui.screen.status.action

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.action.MastodonReportPresenter
import moe.tlaster.precompose.molecule.producePresenter

// @Composable
// @Destination<RootGraph>(
//    style = DestinationStyle.Dialog::class,
//    deepLinks = [
//        DeepLink(
//            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
//        ),
//        DeepLink(
//            uriPattern = AppDeepLink.Mastodon.ReportStatus.ROUTE,
//        ),
//    ],
//    wrappers = [ThemeWrapper::class],
// )
// internal fun MastodonReportRoute(
//    navigator: DestinationsNavigator,
//    userKey: MicroBlogKey,
//    statusKey: MicroBlogKey,
//    accountKey: MicroBlogKey,
// ) {
//    MastodonReportDialog(
//        statusKey = statusKey,
//        userKey = userKey,
//        onBack = {
//            navigator.navigateUp()
//        },
//        accountType = AccountType.Specific(accountKey),
//    )
// }

@Composable
internal fun MastodonReportDialog(
    userKey: MicroBlogKey,
    statusKey: MicroBlogKey?,
    accountType: AccountType,
    onBack: () -> Unit,
) {
    val state by producePresenter("${userKey}_${accountType}_$statusKey") {
        mastodonReportPresenter(
            userKey = userKey,
            statusKey = statusKey,
            accountType = accountType,
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
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onBack,
            ) {
                Text(stringResource(android.R.string.cancel))
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
    accountType: AccountType,
) = run {
    val state =
        remember(userKey, statusKey, accountType) {
            MastodonReportPresenter(
                accountType = accountType,
                userKey = userKey,
                statusKey = statusKey,
            )
        }.invoke()
    state
}
