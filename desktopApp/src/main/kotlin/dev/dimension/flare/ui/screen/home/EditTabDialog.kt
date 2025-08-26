package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.Res
import dev.dimension.flare.cancel
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.RssTimelineTabItem
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.model.res
import dev.dimension.flare.edit_tab_icon
import dev.dimension.flare.edit_tab_name
import dev.dimension.flare.edit_tab_name_placeholder
import dev.dimension.flare.edit_tab_title
import dev.dimension.flare.edit_tab_with_avatar
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ok
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.model.UiRssSource
import io.github.composefluent.component.CheckBox
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.FlyoutContainer
import io.github.composefluent.component.FlyoutPlacement
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun EditTabDialog(
    visible: Boolean,
    tabItem: TabItem,
    onDismissRequest: () -> Unit,
    onConfirm: (TabItem) -> Unit,
) {
    val state by producePresenter(key = "EditTabSheet_$tabItem") {
        presenter(tabItem = tabItem)
    }
    ContentDialog(
        visible = visible,
        title = stringResource(Res.string.edit_tab_title),
        primaryButtonText = stringResource(Res.string.ok),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = {
            when (it) {
                ContentDialogButton.Primary ->
                    tabItem.metaData
                        .copy(
                            title = TitleType.Text(state.text.text.toString()),
                            icon = state.icon,
                        ).let {
                            if (state.canConfirm) {
                                onConfirm(tabItem.update(metaData = it))
                            }
                        }

                ContentDialogButton.Secondary -> Unit
                ContentDialogButton.Close -> onDismissRequest()
            }
        },
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = stringResource(Res.string.edit_tab_icon))
                        if (tabItem.account is AccountType.Specific) {
                            Row(
                                modifier =
                                    Modifier
                                        .clickable {
                                            state.setWithAvatar(!state.withAvatar)
                                        },
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CheckBox(
                                    checked = state.withAvatar,
                                    onCheckStateChange = state::setWithAvatar,
                                )
                                Text(text = stringResource(Res.string.edit_tab_with_avatar))
                            }
                        }
                    }

                    FlyoutContainer(
                        flyout = {
                            LazyHorizontalGrid(
                                rows = GridCells.FixedSize(32.dp),
                                modifier = Modifier.heightIn(max = 120.dp),
                            ) {
                                items(state.availableIcons) { icon ->
                                    SubtleButton(
                                        onClick = {
                                            state.setIcon(icon)
                                        },
                                        iconOnly = true,
                                        modifier = Modifier.padding(4.dp),
                                    ) {
                                        TabIcon(
                                            accountType = tabItem.account,
                                            icon = icon,
                                            title = tabItem.metaData.title,
                                        )
                                    }
                                }
                            }
                        },
                        placement = FlyoutPlacement.BottomAlignedEnd,
                    ) {
                        SubtleButton(
                            onClick = {
                                isFlyoutVisible = true
                            },
                            iconOnly = true,
                        ) {
                            TabIcon(
                                accountType = tabItem.account,
                                icon = state.icon,
                                title = tabItem.metaData.title,
                            )
                        }
                    }
                }
                TextField(
                    state = state.text,
                    modifier = Modifier.fillMaxWidth(),
                    header = {
                        Text(text = stringResource(Res.string.edit_tab_name))
                    },
                    placeholder = {
                        Text(text = stringResource(Res.string.edit_tab_name_placeholder))
                    },
                )
            }
        },
    )
}

@Composable
private fun presenter(tabItem: TabItem) =
    run {
        val text = rememberTextFieldState()
        var icon: IconType by remember {
            mutableStateOf(tabItem.metaData.icon)
        }
        var withAvatar by remember {
            mutableStateOf(tabItem.metaData.icon is IconType.Mixed)
        }
        LaunchedEffect(Unit) {
            val value =
                when (val title = tabItem.metaData.title) {
                    is TitleType.Localized -> getString(title.res)
                    is TitleType.Text -> title.content
                }
            text.edit {
                append(value)
            }
        }
        object {
            val withAvatar = withAvatar
            val availableIcons: ImmutableList<IconType> =
                kotlin
                    .run {
                        when (val account = tabItem.account) {
                            is AccountType.Specific ->
                                listOf(
                                    IconType.Avatar(account.accountKey),
                                    IconType.Url(
                                        UiRssSource.favIconUrl(account.accountKey.host),
                                    ),
                                )

                            else -> emptyList()
                        } +
                            IconType.Material.MaterialIcon.entries.map {
                                IconType.Material(it)
                            } +
                            if (tabItem is RssTimelineTabItem) {
                                listOfNotNull(
                                    IconType.Url(UiRssSource.favIconUrl(tabItem.feedUrl)),
                                )
                            } else {
                                emptyList()
                            }
                    }.let {
                        it.toPersistentList()
                    }
            val text = text
            val icon = icon
            val canConfirm = text.text.isNotEmpty()

            fun setWithAvatar(value: Boolean) {
                withAvatar = value
                setIcon(icon)
            }

            fun setIcon(value: IconType) {
                val account = tabItem.account
                icon =
                    if (withAvatar && account is AccountType.Specific) {
                        when (value) {
                            is IconType.Avatar -> value
                            is IconType.Material ->
                                IconType.Mixed(value.icon, account.accountKey)

                            is IconType.Mixed ->
                                IconType.Mixed(value.icon, account.accountKey)

                            is IconType.Url -> value
                        }
                    } else {
                        when (value) {
                            is IconType.Avatar -> value
                            is IconType.Material -> value
                            is IconType.Mixed -> IconType.Material(value.icon)
                            is IconType.Url -> value
                        }
                    }
            }
        }
    }
