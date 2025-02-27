package dev.dimension.flare.ui.screen.status.action

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.konyaco.fluent.component.ContentDialog
import com.konyaco.fluent.component.ContentDialogButton
import com.konyaco.fluent.component.Text
import dev.dimension.flare.Res
import dev.dimension.flare.cancel
import dev.dimension.flare.mastodon_report_description
import dev.dimension.flare.mastodon_report_title
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.report
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.action.MastodonReportPresenter
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

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
    ContentDialog(
        title = stringResource(Res.string.mastodon_report_title),
        visible = true,
        content = {
            Text(text = stringResource(Res.string.mastodon_report_description))
        },
        primaryButtonText = stringResource(Res.string.report),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = {
            when (it) {
                ContentDialogButton.Primary -> {
                    state.report()
                    onBack.invoke()
                }
                ContentDialogButton.Secondary -> Unit
                ContentDialogButton.Close -> onBack.invoke()
            }
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
