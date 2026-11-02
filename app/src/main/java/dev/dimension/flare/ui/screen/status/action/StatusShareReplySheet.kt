package dev.dimension.flare.ui.screen.status.action

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.status.StatusShareReplyList
import dev.dimension.flare.ui.presenter.status.StatusSharePresenter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatusShareReplySheet(state: StatusSharePresenter.State) {
    ModalBottomSheet(
        onDismissRequest = state::cancelSelection,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(stringResource(R.string.share_replies_title), style = MaterialTheme.typography.headlineSmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.share_replies_count, state.selectedCount),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = state::clearSelection, enabled = state.selectedCount > 0) {
                        Text(stringResource(R.string.share_replies_clear))
                    }
                }
            }
            StatusShareReplyList(state, Modifier.weight(1f, fill = false))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = state::cancelSelection) { Text(stringResource(R.string.share_replies_cancel)) }
                Button(onClick = state::confirmSelection) { Text(stringResource(R.string.share_replies_done)) }
            }
        }
    }
}
