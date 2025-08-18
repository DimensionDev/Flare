package dev.dimension.flare.ui.screen.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.Plus
import dev.dimension.flare.Res
import dev.dimension.flare.list_add
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.more
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.uiListItemComponent
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.list.AllListPresenter
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.SubtleButton
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AllListScreen(
    accountType: AccountType,
    onAddList: () -> Unit,
    toList: (UiList) -> Unit,
) {
    val state by producePresenter("AllListScreen_$accountType") {
        presenter(accountType)
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(FluentTheme.colors.background.mica.base),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.End,
            modifier =
                Modifier
                    .background(FluentTheme.colors.background.layer.default)
                    .padding(8.dp)
                    .fillMaxWidth(),
        ) {
//            SubtleButton(
//                onClick = {
//                    state.refresh()
//                }
//            ) {
//                FAIcon(
//                    FontAwesomeIcons.Solid.ArrowsRotate,
//                    contentDescription = stringResource(Res.string.refresh),
//                )
//            }
            SubtleButton(
                onClick = {
                    onAddList.invoke()
                },
            ) {
                FAIcon(
                    FontAwesomeIcons.Solid.Plus,
                    contentDescription = stringResource(Res.string.list_add),
                )
            }
        }
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(FluentTheme.colors.background.layer.default),
        ) {
            uiListItemComponent(
                state.items,
                onClicked = toList,
                trailingContent = { item ->
                    if (!item.readonly) {
                        SubtleButton(
                            onClick = {
                            },
                        ) {
                            FAIcon(
                                FontAwesomeIcons.Solid.EllipsisVertical,
                                contentDescription = stringResource(Res.string.more),
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun presenter(accountType: AccountType) =
    run {
        remember(accountType) {
            AllListPresenter(accountType)
        }.invoke()
    }
