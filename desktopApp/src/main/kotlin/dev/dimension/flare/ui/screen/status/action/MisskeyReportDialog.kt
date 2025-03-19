package dev.dimension.flare.ui.screen.status.action

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.konyaco.fluent.component.ContentDialog
import com.konyaco.fluent.component.ContentDialogButton
import com.konyaco.fluent.component.Text
import com.konyaco.fluent.component.TextField
import dev.dimension.flare.Res
import dev.dimension.flare.cancel
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ok
import dev.dimension.flare.report_description
import dev.dimension.flare.report_title
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.action.MisskeyReportPresenter
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

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
    var comment by remember { mutableStateOf("") }

    ContentDialog(
        title = stringResource(Res.string.report_title),
        visible = true,
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = stringResource(Res.string.report_description))
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = comment,
                    onValueChange = {
                        comment = it
                    },
                )
            }
        },
        primaryButtonText = stringResource(Res.string.ok),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = {
            when (it) {
                ContentDialogButton.Primary -> {
                    state.report(comment)
                    onBack()
                }
                ContentDialogButton.Secondary -> Unit
                ContentDialogButton.Close -> onBack()
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
