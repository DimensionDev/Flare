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
import dev.dimension.flare.R
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.presenter.status.action.MastodonReportPresenter

@Composable
@Destination(
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
    ],
)
fun MastodonReportRoute(
    navigator: DestinationsNavigator,
    userKey: MicroBlogKey,
    statusKey: MicroBlogKey?,
) {
    MastodonReportDialog(
        statusKey = statusKey,
        userKey = userKey,
        onBack = {
            navigator.navigateUp()
        },
    )
}

@Composable
fun MastodonReportDialog(
    userKey: MicroBlogKey,
    statusKey: MicroBlogKey?,
    onBack: () -> Unit,
) {
    val state by producePresenter("${userKey}_$statusKey") {
        mastodonReportPresenter(userKey, statusKey)
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
) = run {
    val state =
        remember(userKey, statusKey) {
            MastodonReportPresenter(userKey, statusKey)
        }.invoke()
    state
}
