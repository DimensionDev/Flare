package dev.dimension.flare.ui.screen.status.action

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.konyaco.fluent.component.ContentDialog
import com.konyaco.fluent.component.ContentDialogButton
import com.konyaco.fluent.component.Text
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

    ContentDialog(
        title = stringResource(Res.string.report_title),
        visible = true,
        content = {
            Text(text = stringResource(Res.string.report_description))
        },
        primaryButtonText = stringResource(Res.string.ok),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = {
            when (it) {
                ContentDialogButton.Primary -> {
                    state.report()
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
