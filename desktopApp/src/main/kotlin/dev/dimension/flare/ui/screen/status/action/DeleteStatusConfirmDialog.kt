package dev.dimension.flare.ui.screen.status.action

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.konyaco.fluent.component.ContentDialog
import com.konyaco.fluent.component.ContentDialogButton
import com.konyaco.fluent.component.Text
import dev.dimension.flare.Res
import dev.dimension.flare.cancel
import dev.dimension.flare.delete
import dev.dimension.flare.delete_status_message
import dev.dimension.flare.delete_status_title
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.action.DeleteStatusPresenter
import dev.dimension.flare.ui.presenter.status.action.DeleteStatusState
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DeleteStatusConfirmDialog(
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

    ContentDialog(
        title = stringResource(Res.string.delete_status_title),
        visible = true,
        content = {
            Text(text = stringResource(Res.string.delete_status_message))
        },
        primaryButtonText = stringResource(Res.string.delete),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = {
            when (it) {
                ContentDialogButton.Primary -> {
                    state.delete()
                    onBack.invoke()
                }
                ContentDialogButton.Secondary -> Unit
                ContentDialogButton.Close -> onBack.invoke()
            }
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
