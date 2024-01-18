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
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.action.MisskeyReportPresenter

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
fun MisskeyReportRoute(
    navigator: DestinationsNavigator,
    userKey: MicroBlogKey,
    statusKey: MicroBlogKey?,
) {
    MisskeyReportDialog(
        statusKey = statusKey,
        userKey = userKey,
        onBack = {
            navigator.navigateUp()
        },
    )
}

@Composable
fun MisskeyReportDialog(
    userKey: MicroBlogKey,
    statusKey: MicroBlogKey?,
    onBack: () -> Unit,
) {
    val state by producePresenter("${userKey}_${statusKey ?: ""}") {
        misskeyReportPresenter(
            userKey,
            statusKey,
        )
    }

    AlertDialog(
        title = {
            Text(
                text = stringResource(R.string.report_title),
            )
        },
        text = {
            Text(
                text = stringResource(R.string.report_description),
            )
        },
        onDismissRequest = onBack,
        confirmButton = {
            TextButton(
                onClick = {
                    state.report()
                    onBack()
                },
            ) {
                Text(
                    text = stringResource(R.string.confirm),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onBack,
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                )
            }
        },
    )
}

@Composable
private fun misskeyReportPresenter(
    userKey: MicroBlogKey,
    statusKey: MicroBlogKey?,
) = run {
    remember(userKey, statusKey) {
        MisskeyReportPresenter(
            userKey,
            statusKey,
        )
    }.invoke()
}
