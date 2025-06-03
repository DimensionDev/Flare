package dev.dimension.flare.ui.screen.status.action

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.OutlinedTextField2
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.action.MisskeyReportPresenter
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun MisskeyReportDialog(
    userKey: MicroBlogKey,
    statusKey: MicroBlogKey?,
    accountType: AccountType,
    onBack: () -> Unit,
) {
    val state by producePresenter("${userKey}_${statusKey ?: ""}_$accountType") {
        misskeyReportPresenter(
            userKey,
            statusKey,
            accountType,
        )
    }
    val comment = rememberTextFieldState()

    AlertDialog(
        title = {
            Text(
                text = stringResource(R.string.report_title),
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.report_description),
                )
                OutlinedTextField2(
                    state = comment,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        onDismissRequest = onBack,
        confirmButton = {
            TextButton(
                onClick = {
                    state.report(
                        comment = comment.text.toString(),
                    )
                    onBack()
                },
            ) {
                Text(
                    text = stringResource(android.R.string.ok),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onBack,
            ) {
                Text(
                    text = stringResource(android.R.string.cancel),
                )
            }
        },
    )
}

@Composable
private fun misskeyReportPresenter(
    userKey: MicroBlogKey,
    statusKey: MicroBlogKey?,
    accountType: AccountType,
) = run {
    remember(userKey, statusKey, accountType) {
        MisskeyReportPresenter(
            userKey = userKey,
            statusKey = statusKey,
            accountType = accountType,
        )
    }.invoke()
}
