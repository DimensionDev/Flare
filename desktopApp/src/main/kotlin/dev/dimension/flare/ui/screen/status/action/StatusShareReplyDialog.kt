package dev.dimension.flare.ui.screen.status.action

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.Res
import dev.dimension.flare.share_replies_cancel
import dev.dimension.flare.share_replies_clear
import dev.dimension.flare.share_replies_count
import dev.dimension.flare.share_replies_done
import dev.dimension.flare.share_replies_title
import dev.dimension.flare.ui.component.status.StatusShareReplyList
import dev.dimension.flare.ui.presenter.status.StatusSharePresenter
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.DialogSize
import io.github.composefluent.component.Text
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StatusShareReplyDialog(state: StatusSharePresenter.State) {
    ContentDialog(
        title = stringResource(Res.string.share_replies_title),
        visible = true,
        size = DialogSize.Max,
        content = {
            Column {
                Text(stringResource(Res.string.share_replies_count, state.selectedCount))
                StatusShareReplyList(state, Modifier.fillMaxWidth().heightIn(max = 440.dp))
            }
        },
        primaryButtonText = stringResource(Res.string.share_replies_done),
        secondaryButtonText = stringResource(Res.string.share_replies_clear),
        closeButtonText = stringResource(Res.string.share_replies_cancel),
        onButtonClick = {
            when (it) {
                ContentDialogButton.Primary -> state.confirmSelection()
                ContentDialogButton.Secondary -> state.clearSelection()
                ContentDialogButton.Close -> state.cancelSelection()
            }
        },
    )
}
