package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleMinus
import compose.icons.fontawesomeicons.solid.CirclePlus
import compose.icons.fontawesomeicons.solid.Plus
import dev.dimension.flare.Res
import dev.dimension.flare.add_rss_source
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.ok
import dev.dimension.flare.rss_title
import dev.dimension.flare.tab_settings_add
import dev.dimension.flare.tab_settings_remove
import dev.dimension.flare.ui.common.itemsIndexed
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.model.asText
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.screen.settings.AllTabsPresenter
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.ButtonDefaults
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.LiteFilter
import io.github.composefluent.component.PillButton
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import dev.dimension.flare.ui.component.Text as UiText

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun AddTabDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    tabs: ImmutableList<TimelineTabItemV2>,
    allTabs: AllTabsPresenter.State,
    onAddTab: (TimelineTabItemV2) -> Unit,
    onDeleteTab: (String) -> Unit,
    toAddRssSource: () -> Unit,
) {
    ContentDialog(
        visible = visible,
        title = stringResource(Res.string.tab_settings_add),
        primaryButtonText = stringResource(Res.string.ok),
        onButtonClick = {
            onDismiss.invoke()
        },
        content = {
            @Composable
            fun TabItem(
                tabItem: TimelineTabItemV2,
                modifier: Modifier = Modifier,
            ) {
                ListTabItem(
                    data = tabItem,
                    isAdded = tabs.any { tab -> tabItem.id == tab.id },
                    modifier =
                        modifier.clickable {
                            if (tabs.any { tab -> tabItem.id == tab.id }) {
                                onDeleteTab(tabItem.id)
                            } else {
                                onAddTab(tabItem)
                            }
                        },
                )
            }
            Column(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                allTabs.accountTabs.onSuccess { accountTabGroups ->
                    var selectedPage by remember(accountTabGroups) { mutableIntStateOf(0) }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        LazyColumn(
                            modifier = Modifier.width(140.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            item {
                                SubtleButton(
                                    buttonColors =
                                        if (selectedPage == 0) {
                                            ButtonDefaults.accentButtonColors()
                                        } else {
                                            ButtonDefaults.subtleButtonColors()
                                        },
                                    onClick = {
                                        selectedPage = 0
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = stringResource(Res.string.rss_title),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            itemsIndexed(accountTabGroups) { index, tab ->
                                SubtleButton(
                                    buttonColors =
                                        if (selectedPage == index + 1) {
                                            ButtonDefaults.accentButtonColors()
                                        } else {
                                            ButtonDefaults.subtleButtonColors()
                                        },
                                    onClick = {
                                        selectedPage = index + 1
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                        horizontalAlignment = Alignment.Start,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            AvatarComponent(
                                                tab.profile.avatar,
                                                size = 24.dp,
                                            )
                                            RichText(
                                                text = tab.profile.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Text(
                                            text = tab.profile.handle.normalizedHost,
                                            style = FluentTheme.typography.caption,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                        ) {
                            if (selectedPage == 0) {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    itemsIndexed(
                                        allTabs.rssTabs,
                                    ) { index, tab ->
                                        TabItem(
                                            tab,
                                            modifier =
                                                Modifier
                                                    .listCard(
                                                        index = index,
                                                        totalCount = allTabs.rssTabs.size,
                                                    ),
                                        )
                                    }
                                    if (allTabs.rssTabs.isNotEmpty()) {
                                        item {
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                    }
                                    item {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            AccentButton(
                                                onClick = toAddRssSource,
                                            ) {
                                                FAIcon(
                                                    FontAwesomeIcons.Solid.Plus,
                                                    contentDescription = stringResource(Res.string.add_rss_source),
                                                )
                                                Text(stringResource(Res.string.add_rss_source))
                                            }
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    val accountTabs = accountTabGroups[selectedPage - 1]
                                    var selectedIndex by remember(accountTabs.profile.key) {
                                        mutableIntStateOf(0)
                                    }
                                    if (accountTabs.tabs.size > 1) {
                                        LiteFilter(
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            accountTabs.tabs.forEachIndexed { index, section ->
                                                PillButton(
                                                    selected = selectedIndex == index,
                                                    onSelectedChanged = { selectedIndex = index },
                                                ) {
                                                    UiText(section.title.asText())
                                                }
                                            }
                                        }
                                    }
                                    val section = accountTabs.tabs.elementAtOrNull(selectedIndex)
                                    if (section != null) {
                                        LazyColumn(
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            itemsIndexed(section.data) { index, totalCount, item ->
                                                TabItem(
                                                    item,
                                                    modifier =
                                                        Modifier
                                                            .listCard(
                                                                index = index,
                                                                totalCount = totalCount,
                                                            ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
internal fun ListTabItem(
    data: TimelineTabItemV2,
    isAdded: Boolean,
    modifier: Modifier = Modifier,
) {
    CardExpanderItem(
        heading = {
            UiText(data.title)
        },
        icon = {
            TabIcon(data)
        },
        modifier = modifier,
        trailing = {
            if (isAdded) {
                FAIcon(
                    FontAwesomeIcons.Solid.CircleMinus,
                    contentDescription = stringResource(Res.string.tab_settings_remove),
                )
            } else {
                FAIcon(
                    FontAwesomeIcons.Solid.CirclePlus,
                    contentDescription = stringResource(Res.string.tab_settings_add),
                )
            }
        },
    )
}
