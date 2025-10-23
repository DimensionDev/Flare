package dev.dimension.flare.ui.screen.status.action

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import dev.dimension.flare.Res
import dev.dimension.flare.cancel
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.EmojiPicker
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.action.AddReactionPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.FluentDialog
import io.github.composefluent.component.Text
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AddReactionSheet(
    statusKey: MicroBlogKey,
    accountType: AccountType,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by producePresenter("AddReactionSheet_${accountType}_$statusKey") {
        presenter(
            statusKey = statusKey,
            accountType = accountType,
        )
    }
    FluentDialog(
        visible = true,
//        onDismissRequest = onBack,
    ) {
        state.emojis.onSuccess {
            Column(
                modifier =
                    modifier
                        .onKeyEvent {
                            if (it.key == androidx.compose.ui.input.key.Key.Escape) {
                                // Escape key
                                onBack()
                                true
                            } else {
                                false
                            }
                        }.size(
                            width = 400.dp,
                            height = 500.dp,
                        ).padding(
                            horizontal = screenHorizontalPadding,
                            vertical = 8.dp,
                        ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EmojiPicker(
                    data = it.data,
                    accountType = accountType,
                    onEmojiSelected = {
                        state.select(it)
                        onBack()
                    },
                    modifier = Modifier.weight(1f),
                )
                AccentButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(Res.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun presenter(
    statusKey: MicroBlogKey,
    accountType: AccountType,
) = run {
    remember(statusKey, accountType) {
        AddReactionPresenter(
            accountType = accountType,
            statusKey = statusKey,
        )
    }.invoke()
}
