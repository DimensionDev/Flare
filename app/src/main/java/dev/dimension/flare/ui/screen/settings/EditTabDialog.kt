package dev.dimension.flare.ui.screen.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import dev.dimension.flare.R
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.RssTimelineTabItem
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.model.resId
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.FlareDropdownMenu
import dev.dimension.flare.ui.model.UiRssSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

@Composable
internal fun EditTabDialog(
    tabItem: TabItem,
    onDismissRequest: () -> Unit,
    onConfirm: (TabItem) -> Unit,
) {
    val state by producePresenter(key = "EditTabSheet_$tabItem") {
        presenter(tabItem = tabItem)
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = state.canConfirm,
                onClick = {
                    tabItem.metaData
                        .copy(
                            title = TitleType.Text(state.text.text.toString()),
                            icon = state.icon,
                        ).let {
                            onConfirm(tabItem.update(metaData = it))
                        }
                },
            ) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        },
        text = {
            Column {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(id = R.string.edit_tab_icon))
                    },
                    supportingContent =
                        if (tabItem.account is AccountType.Specific) {
                            {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                state.setWithAvatar(!state.withAvatar)
                                            },
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = state.withAvatar,
                                        onCheckedChange = state::setWithAvatar,
                                    )
                                    Text(text = stringResource(id = R.string.edit_tab_with_avatar))
                                }
                            }
                        } else {
                            null
                        },
                    trailingContent = {
                        IconButton(onClick = {
                            state.setShowIconPicker(true)
                        }) {
                            TabIcon(
                                accountType = tabItem.account,
                                icon = state.icon,
                                title = tabItem.metaData.title,
                            )
                        }
                        FlareDropdownMenu(
                            expanded = state.showIconPicker,
                            onDismissRequest = {
                                state.setShowIconPicker(false)
                            },
                        ) {
                            state.availableIcons.let { icons ->
                                icons.forEach { icon ->
                                    DropdownMenuItem(
                                        text = {
                                            TabIcon(
                                                accountType = tabItem.account,
                                                icon = icon,
                                                title = tabItem.metaData.title,
                                            )
                                        },
                                        onClick = {
                                            state.setIcon(icon)
                                            state.setShowIconPicker(false)
                                        },
                                    )
                                }
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                OutlinedTextField(
                    state = state.text,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(text = stringResource(id = R.string.edit_tab_name))
                    },
                    placeholder = {
                        Text(text = stringResource(id = R.string.edit_tab_name_placeholder))
                    },
                )
            }
        },
        title = {
            Text(text = stringResource(id = R.string.edit_tab_title))
        },
    )
}

@Composable
private fun presenter(
    tabItem: TabItem,
    context: Context = koinInject(),
    repository: SettingsRepository = koinInject(),
    appScope: CoroutineScope = koinInject(),
) = run {
    val text = rememberTextFieldState()
    var icon: IconType by remember {
        mutableStateOf(tabItem.metaData.icon)
    }
    var withAvatar by remember {
        mutableStateOf(tabItem.metaData.icon is IconType.Mixed)
    }
    var showIconPicker by remember {
        mutableStateOf(false)
    }
    LaunchedEffect(Unit) {
        val value =
            when (val title = tabItem.metaData.title) {
                is TitleType.Localized -> context.getString(title.resId)
                is TitleType.Text -> title.content
            }
        text.edit {
            append(value)
        }
    }
    object {
        val showIconPicker = showIconPicker
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

        fun setShowIconPicker(value: Boolean) {
            showIconPicker = value
        }
    }
}
