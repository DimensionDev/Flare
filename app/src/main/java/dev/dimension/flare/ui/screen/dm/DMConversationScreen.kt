package dev.dimension.flare.ui.screen.dm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.PaperPlane
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.common.items
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.HtmlText
import dev.dimension.flare.ui.component.OutlinedTextField2
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.localizedShortTime
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.dm.DMConversationPresenter
import dev.dimension.flare.ui.presenter.dm.DMConversationState
import dev.dimension.flare.ui.presenter.invoke

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DMConversationScreen(
    accountType: AccountType,
    id: String,
    onBack: () -> Unit,
) {
    val state by producePresenter(
        key = "dm_conversation_${accountType}_$id",
    ) {
        presenter(
            accountType = accountType,
            id = id,
        )
    }

    FlareScaffold(
        topBar = {
            FlareTopAppBar(
                title = {
                    state.users.onSuccess {
//                        HtmlText(
//                            element = it.name.data,
//                            maxLines = 1,
//                        )
                    }
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
            )
        },
        bottomBar = {
            OutlinedTextField2(
                state = state.text,
                lineLimits = TextFieldLineLimits.SingleLine,
                trailingIcon = {
                    IconButton(onClick = {
                        state.send()
                    }) {
                        FAIcon(
                            FontAwesomeIcons.Solid.PaperPlane,
                            contentDescription = stringResource(id = R.string.send),
                        )
                    }
                },
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.dm_send_placeholder),
                    )
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            reverseLayout = true,
            contentPadding = contentPadding,
            modifier =
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .imeNestedScroll(),
        ) {
            items(
                state.items,
                key = {
                    get(it)?.id ?: it
                },
//                emptyContent = {
//
//                },
//                errorContent = {
//
//                },
//                loadingContent = {
//
//                },
                itemContent = { item ->
                    DMItem(item)
                },
            )
        }
    }
}

@Composable
private fun DMItem(
    item: UiDMItem,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth(),
        horizontalAlignment =
            if (item.isFromMe) {
                Alignment.End
            } else {
                Alignment.Start
            },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.75f),
            contentAlignment =
                if (item.isFromMe) {
                    Alignment.CenterEnd
                } else {
                    Alignment.CenterStart
                },
        ) {
            when (val message = item.content) {
                is UiDMItem.Message.Text ->
                    HtmlText(
                        element = message.text.data,
                        modifier =
                            Modifier
                                .background(
                                    color =
                                        if (item.isFromMe) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceDim
                                        },
                                    shape =
                                        MaterialTheme.shapes.large.let {
                                            if (item.isFromMe) {
                                                it.copy(
                                                    bottomEnd = CornerSize(0.dp),
                                                )
                                            } else {
                                                it.copy(
                                                    bottomStart = CornerSize(0.dp),
                                                )
                                            }
                                        },
                                ).padding(
                                    vertical = 8.dp,
                                    horizontal = 16.dp,
                                ),
                        color =
                            if (item.isFromMe) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )

                UiDMItem.Message.Deleted ->
                    Text(
                        text = stringResource(id = R.string.dm_deleted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
        }
        Text(
            item.timestamp.shortTime.localizedShortTime,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun presenter(
    accountType: AccountType,
    id: String,
) = run {
    val text = rememberTextFieldState()
    val state =
        remember(
            accountType,
            id,
        ) {
            DMConversationPresenter(
                accountType = accountType,
                id = id,
            )
        }.invoke()

    object : DMConversationState by state {
        val text = text

        fun send() {
            send(text.text.toString())
            text.clearText()
        }
    }
}
