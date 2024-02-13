package dev.dimension.flare.ui.screen.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eygraber.compose.placeholder.material3.placeholder
import com.ramcosta.composedestinations.annotation.Destination
import dev.dimension.flare.R
import dev.dimension.flare.data.model.AccountType
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.TabSettings
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.invoke
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyColumnState

@Destination(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun TabCustomizeRoute(navigator: ProxyDestinationsNavigator) {
    TabCustomizeScreen(
        onBack = navigator::navigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TabCustomizeScreen(onBack: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val state by producePresenter { presenter() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_tab_customization))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                    }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(id = R.string.tab_settings_add),
                        )
                    }
                },
            )
        },
    ) {
        val lazyListState = rememberLazyListState()
        val reorderableLazyColumnState =
            rememberReorderableLazyColumnState(lazyListState) { from, to ->
                state.moveTab(from.index, to.index)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        LazyColumn(
            state = lazyListState,
            contentPadding = it,
        ) {
            items(state.tabs, key = { it.key }) { item ->
                ReorderableItem(reorderableLazyColumnState, key = item.key) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)

                    Surface(shadowElevation = elevation) {
                        ListItem(
                            headlineContent = {
                                TabTitle(item.metaData.title)
                            },
                            leadingContent = {
                                TabIcon(item.account, item.metaData.icon, item.metaData.title)
                            },
                            trailingContent = {
                                IconButton(
                                    modifier =
                                        Modifier.draggableHandle(
                                            onDragStarted = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragStopped = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                        ),
                                    onClick = {},
                                ) {
                                    Icon(Icons.Rounded.DragHandle, contentDescription = "Reorder")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TabTitle(
    title: TitleType,
    modifier: Modifier = Modifier,
) {
    Text(
        text =
            when (title) {
                is TitleType.Localized -> stringResource(id = title.resId)
                is TitleType.Text -> title.content
            },
        modifier = modifier,
    )
}

@Composable
fun TabIcon(
    accountType: AccountType,
    icon: IconType,
    title: TitleType,
    modifier: Modifier = Modifier,
) {
    when (icon) {
        is IconType.Avatar -> {
            val accountKey =
                when (accountType) {
                    AccountType.Active -> null
                    is AccountType.Specific -> accountType.accountKey
                }
            val userState by producePresenter { UserPresenter(accountKey, icon.userKey).invoke() }
            userState.user.onSuccess {
                AvatarComponent(it.avatarUrl, size = 24.dp, modifier = modifier)
            }.onLoading {
                AvatarComponent(null, size = 24.dp, modifier = modifier.placeholder(true))
            }
        }

        is IconType.Material -> {
            Icon(
                imageVector = icon.icon.toIcon(),
                contentDescription =
                    when (title) {
                        is TitleType.Localized -> stringResource(id = title.resId)
                        is TitleType.Text -> title.content
                    },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun presenter(repository: SettingsRepository = koinInject()) =
    run {
        val scope = rememberCoroutineScope()
        val tabSettings by repository.tabSettings.collectAsState(TabSettings())

        object {
            val tabs = tabSettings.items.toImmutableList()

            fun moveTab(
                from: Int,
                to: Int,
            ) {
                scope.launch {
                    repository.updateTabSettings {
                        val newTabs = items.toMutableList()
                        newTabs.add(to, newTabs.removeAt(from))
                        copy(items = newTabs.toImmutableList())
                    }
                }
            }
        }
    }
