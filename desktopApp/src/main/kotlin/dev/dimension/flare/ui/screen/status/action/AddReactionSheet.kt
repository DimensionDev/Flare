package dev.dimension.flare.ui.screen.status.action

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.konyaco.fluent.component.Flyout
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.EmojiPicker
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.action.AddReactionPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import moe.tlaster.precompose.molecule.producePresenter

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
    Flyout(
        visible = true,
        onDismissRequest = onBack,
    ) {
        state.emojis.onSuccess {
            EmojiPicker(
                data = it.data,
                accountType = accountType,
                onEmojiSelected = {
                    state.select(it)
                    onBack()
                },
                modifier =
                    modifier
                        .padding(
                            horizontal = screenHorizontalPadding,
                            vertical = 8.dp,
                        ),
            )
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
