package dev.dimension.flare.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.Res
import dev.dimension.flare.block_user_description
import dev.dimension.flare.block_user_title
import dev.dimension.flare.cancel
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.mute_user_description
import dev.dimension.flare.mute_user_title
import dev.dimension.flare.ok
import dev.dimension.flare.report_user_description
import dev.dimension.flare.report_user_title
import dev.dimension.flare.ui.presenter.profile.BlockUserPresenter
import dev.dimension.flare.ui.presenter.profile.MuteUserPresenter
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.Text
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BlockUserDialog(
    accountType: AccountType?,
    userKey: MicroBlogKey,
    onBack: () -> Unit,
) {
    val state by producePresenter("block_user_${accountType}_$userKey") {
        remember {
            BlockUserPresenter(accountType, userKey)
        }.body()
    }
    ContentDialog(
        title = stringResource(Res.string.block_user_title),
        visible = true,
        content = {
            Text(stringResource(Res.string.block_user_description))
        },
        primaryButtonText = stringResource(Res.string.ok),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = {
            when (it) {
                ContentDialogButton.Primary -> {
                    state.confirm()
                    onBack.invoke()
                }
                ContentDialogButton.Secondary -> onBack.invoke()
                ContentDialogButton.Close -> onBack.invoke()
            }
        },
    )
}

@Composable
internal fun MuteUserDialog(
    accountType: AccountType?,
    userKey: MicroBlogKey,
    onBack: () -> Unit,
) {
    val state by producePresenter("mute_user_${accountType}_$userKey") {
        remember {
            MuteUserPresenter(accountType, userKey)
        }.body()
    }

    ContentDialog(
        title = stringResource(Res.string.mute_user_title),
        visible = true,
        content = {
            Text(stringResource(Res.string.mute_user_description))
        },
        primaryButtonText = stringResource(Res.string.ok),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = {
            when (it) {
                ContentDialogButton.Primary -> {
                    state.confirm()
                    onBack.invoke()
                }
                ContentDialogButton.Secondary -> onBack.invoke()
                ContentDialogButton.Close -> onBack.invoke()
            }
        },
    )
}

@Composable
internal fun ReportUserDialog(
    accountType: AccountType?,
    userKey: MicroBlogKey,
    onBack: () -> Unit,
) {
    ContentDialog(
        title = stringResource(Res.string.report_user_title),
        visible = true,
        content = {
            Text(stringResource(Res.string.report_user_description))
        },
        primaryButtonText = stringResource(Res.string.ok),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = {
            when (it) {
                ContentDialogButton.Primary -> {
                    onBack.invoke()
                }
                ContentDialogButton.Secondary -> onBack.invoke()
                ContentDialogButton.Close -> onBack.invoke()
            }
        },
    )
}
