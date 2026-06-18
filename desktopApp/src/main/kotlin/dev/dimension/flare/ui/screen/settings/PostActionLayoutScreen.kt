package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bars
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.datasource.microblog.PostActionLayoutConfig
import dev.dimension.flare.data.datasource.microblog.PostActionLayoutHelpers
import dev.dimension.flare.data.datasource.microblog.PostActionPlacement
import dev.dimension.flare.data.model.appearance.AppearanceKeys
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.settings_post_action_family_block_user
import dev.dimension.flare.settings_post_action_family_bookmark
import dev.dimension.flare.settings_post_action_family_comment
import dev.dimension.flare.settings_post_action_family_delete
import dev.dimension.flare.settings_post_action_family_favorite
import dev.dimension.flare.settings_post_action_family_fx_share
import dev.dimension.flare.settings_post_action_family_like
import dev.dimension.flare.settings_post_action_family_mute_user
import dev.dimension.flare.settings_post_action_family_quote
import dev.dimension.flare.settings_post_action_family_react
import dev.dimension.flare.settings_post_action_family_reply
import dev.dimension.flare.settings_post_action_family_report
import dev.dimension.flare.settings_post_action_family_repost
import dev.dimension.flare.settings_post_action_family_share
import dev.dimension.flare.settings_post_action_family_translate
import dev.dimension.flare.settings_post_action_layout_button_row
import dev.dimension.flare.settings_post_action_layout_empty
import dev.dimension.flare.settings_post_action_layout_enable
import dev.dimension.flare.settings_post_action_layout_enable_description
import dev.dimension.flare.settings_post_action_layout_hidden
import dev.dimension.flare.settings_post_action_layout_hide_action
import dev.dimension.flare.settings_post_action_layout_more_menu
import dev.dimension.flare.settings_post_action_layout_move_down
import dev.dimension.flare.settings_post_action_layout_move_to_button_row
import dev.dimension.flare.settings_post_action_layout_move_to_more_menu
import dev.dimension.flare.settings_post_action_layout_move_up
import dev.dimension.flare.tab_settings_drag
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.component.Header
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.component.toImageVector
import dev.dimension.flare.ui.model.PostActionLayoutPreviewHelper
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.AppearancePresenter
import dev.dimension.flare.ui.presenter.settings.AppearanceState
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.FlyoutPlacement
import io.github.composefluent.component.MenuFlyout
import io.github.composefluent.component.MenuFlyoutItem
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import io.github.composefluent.surface.Card
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
internal fun PostActionLayoutScreen() {
    val state by producePresenter { postActionLayoutPresenter() }
    val timelineAppearance = LocalTimelineAppearance.current
    val persistedConfig = PostActionLayoutHelpers.normalizedForEdit(timelineAppearance.postActionLayout)
    var config by remember { mutableStateOf(persistedConfig) }
    var isDraggingAction by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val reorderableState =
        rememberReorderableLazyListState(listState) { from, to ->
            val fromFamily = from.key.asPostActionFamilyOrNull() ?: return@rememberReorderableLazyListState
            val toFamily = to.key.asPostActionFamilyOrNull() ?: return@rememberReorderableLazyListState
            val fromPlacement = PostActionLayoutHelpers.placementOf(config, fromFamily)
            val toPlacement = PostActionLayoutHelpers.placementOf(config, toFamily)
            if (fromPlacement == toPlacement) {
                updateConfig(
                    value = PostActionLayoutHelpers.moveWithin(config, fromPlacement, fromFamily, toFamily),
                    persist = false,
                    updateLocal = { config = it },
                    persistConfig = state::updatePostActionLayout,
                )
            }
        }

    LaunchedEffect(PostActionLayoutHelpers.signature(persistedConfig)) {
        if (!isDraggingAction) {
            config = persistedConfig
        }
    }

    fun commitConfig() {
        updateConfig(
            value = config,
            persist = true,
            updateLocal = { config = it },
            persistConfig = state::updatePostActionLayout,
        )
    }

    FlareScrollBar(listState) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = LocalWindowPadding.current + PaddingValues(vertical = 12.dp),
        ) {
            item(key = "preview") {
                state.sampleStatus.onSuccess { sample ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CompositionLocalProvider(
                            LocalTimelineAppearance provides timelineAppearance.copy(postActionLayout = config),
                        ) {
                            StatusItem(
                                item = PostActionLayoutPreviewHelper.withPreviewActions(sample),
                            )
                        }
                    }
                }
            }
            item(key = "enabled") {
                CardExpanderItem(
                    icon = null,
                    heading = {
                        Text(stringResource(Res.string.settings_post_action_layout_enable))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_post_action_layout_enable_description))
                    },
                    trailing = {
                        Switcher(
                            checked = config.enabled,
                            onCheckStateChange = {
                                updateConfig(
                                    value = PostActionLayoutHelpers.withEnabled(config, it),
                                    updateLocal = { value -> config = value },
                                    persistConfig = state::updatePostActionLayout,
                                )
                            },
                            textBefore = true,
                        )
                    },
                )
            }

            if (config.enabled) {
                postActionSection(
                    title = Res.string.settings_post_action_layout_button_row,
                    placement = PostActionPlacement.ButtonRow,
                    config = config,
                    onConfigChanged = {
                        updateConfig(
                            value = it,
                            updateLocal = { value -> config = value },
                            persistConfig = state::updatePostActionLayout,
                        )
                    },
                    onDragStarted = {
                        isDraggingAction = true
                    },
                    onDragStopped = {
                        isDraggingAction = false
                        commitConfig()
                    },
                    reorderableState = reorderableState,
                )
                postActionSection(
                    title = Res.string.settings_post_action_layout_more_menu,
                    placement = PostActionPlacement.MoreMenu,
                    config = config,
                    onConfigChanged = {
                        updateConfig(
                            value = it,
                            updateLocal = { value -> config = value },
                            persistConfig = state::updatePostActionLayout,
                        )
                    },
                    onDragStarted = {
                        isDraggingAction = true
                    },
                    onDragStopped = {
                        isDraggingAction = false
                        commitConfig()
                    },
                    reorderableState = reorderableState,
                )
                postActionSection(
                    title = Res.string.settings_post_action_layout_hidden,
                    placement = PostActionPlacement.Hidden,
                    config = config,
                    onConfigChanged = {
                        updateConfig(
                            value = it,
                            updateLocal = { value -> config = value },
                            persistConfig = state::updatePostActionLayout,
                        )
                    },
                    onDragStarted = {
                        isDraggingAction = true
                    },
                    onDragStopped = {
                        isDraggingAction = false
                        commitConfig()
                    },
                    reorderableState = reorderableState,
                )
            }
        }
    }
}

private fun LazyListScope.postActionSection(
    title: StringResource,
    placement: PostActionPlacement,
    config: PostActionLayoutConfig,
    onConfigChanged: (PostActionLayoutConfig) -> Unit,
    onDragStarted: () -> Unit,
    onDragStopped: () -> Unit,
    reorderableState: ReorderableLazyListState,
) {
    val families = PostActionLayoutHelpers.familiesFor(config, placement)
    item(key = "${placement.name}_header") {
        Header(
            text = stringResource(title),
            modifier = Modifier.padding(top = 16.dp),
        )
    }
    if (families.isEmpty()) {
        item(key = "${placement.name}_empty") {
            CardExpanderItem(
                icon = null,
                heading = {
                    Text(stringResource(Res.string.settings_post_action_layout_empty))
                },
            )
        }
    } else {
        itemsIndexed(
            items = families,
            key = { _, family -> family.saveableKey },
        ) { index, family ->
            PostActionFamilyRow(
                family = family,
                placement = placement,
                index = index,
                totalCount = families.size,
                config = config,
                onConfigChanged = onConfigChanged,
                onDragStarted = onDragStarted,
                onDragStopped = onDragStopped,
                reorderableState = reorderableState,
            )
        }
    }
}

@Composable
private fun LazyItemScope.PostActionFamilyRow(
    family: PostActionFamily,
    placement: PostActionPlacement,
    index: Int,
    totalCount: Int,
    config: PostActionLayoutConfig,
    onConfigChanged: (PostActionLayoutConfig) -> Unit,
    onDragStarted: () -> Unit,
    onDragStopped: () -> Unit,
    reorderableState: ReorderableLazyListState,
) {
    ReorderableItem(reorderableState, key = family.saveableKey) { _ ->
        var isFlyoutVisible by remember { mutableStateOf(false) }
        CardExpanderItem(
            icon = {
                FAIcon(
                    imageVector = family.icon.toImageVector(),
                    contentDescription = null,
                )
            },
            heading = {
                Text(stringResource(family.label))
            },
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SubtleButton(
                        modifier =
                            Modifier.draggableHandle(
                                onDragStarted = {
                                    onDragStarted()
                                },
                                onDragStopped = onDragStopped,
                            ),
                        onClick = {},
                        iconOnly = true,
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Bars,
                            contentDescription = stringResource(Res.string.tab_settings_drag),
                        )
                    }
                    Box {
                        SubtleButton(
                            onClick = {
                                isFlyoutVisible = true
                            },
                            iconOnly = true,
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.EllipsisVertical,
                                contentDescription = null,
                            )
                        }
                        MenuFlyout(
                            visible = isFlyoutVisible,
                            onDismissRequest = {
                                isFlyoutVisible = false
                            },
                            placement = FlyoutPlacement.BottomAlignedEnd,
                        ) {
                            postActionPlacements.forEach { target ->
                                if (target != placement) {
                                    MenuFlyoutItem(
                                        text = { Text(stringResource(target.moveLabel)) },
                                        onClick = {
                                            isFlyoutVisible = false
                                            onConfigChanged(
                                                PostActionLayoutHelpers.moveTo(config, family, target),
                                            )
                                        },
                                    )
                                }
                            }
                            if (index > 0) {
                                MenuFlyoutItem(
                                    text = { Text(stringResource(Res.string.settings_post_action_layout_move_up)) },
                                    onClick = {
                                        isFlyoutVisible = false
                                        onConfigChanged(PostActionLayoutHelpers.moveBy(config, family, -1))
                                    },
                                )
                            }
                            if (index < totalCount - 1) {
                                MenuFlyoutItem(
                                    text = { Text(stringResource(Res.string.settings_post_action_layout_move_down)) },
                                    onClick = {
                                        isFlyoutVisible = false
                                        onConfigChanged(PostActionLayoutHelpers.moveBy(config, family, 1))
                                    },
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun postActionLayoutPresenter() =
    run {
        val scope = rememberCoroutineScope()
        val settingsRepository = koinInject<SettingsRepository>()
        val appearanceState = remember { AppearancePresenter() }.invoke()
        object : AppearanceState by appearanceState {
            fun updatePostActionLayout(value: PostActionLayoutConfig) {
                scope.launch {
                    settingsRepository.updateAppearance(AppearanceKeys.PostActionLayout, value)
                }
            }
        }
    }

private fun updateConfig(
    value: PostActionLayoutConfig,
    persist: Boolean = true,
    updateLocal: (PostActionLayoutConfig) -> Unit,
    persistConfig: (PostActionLayoutConfig) -> Unit,
) {
    val normalized = PostActionLayoutHelpers.normalizedForEdit(value)
    updateLocal(normalized)
    if (persist) {
        persistConfig(normalized)
    }
}

private val postActionPlacements: List<PostActionPlacement> =
    listOf(
        PostActionPlacement.ButtonRow,
        PostActionPlacement.MoreMenu,
        PostActionPlacement.Hidden,
    )

private const val POST_ACTION_FAMILY_KEY_PREFIX = "post_action_family:"

private val PostActionFamily.saveableKey: String
    get() = "$POST_ACTION_FAMILY_KEY_PREFIX$name"

private fun Any?.asPostActionFamilyOrNull(): PostActionFamily? {
    val key = this as? String ?: return null
    if (!key.startsWith(POST_ACTION_FAMILY_KEY_PREFIX)) return null
    return runCatching {
        enumValueOf<PostActionFamily>(key.removePrefix(POST_ACTION_FAMILY_KEY_PREFIX))
    }.getOrNull()
}

private val PostActionPlacement.moveLabel: StringResource
    get() =
        when (this) {
            PostActionPlacement.ButtonRow -> Res.string.settings_post_action_layout_move_to_button_row
            PostActionPlacement.MoreMenu -> Res.string.settings_post_action_layout_move_to_more_menu
            PostActionPlacement.Hidden -> Res.string.settings_post_action_layout_hide_action
        }

private val PostActionFamily.icon: UiIcon
    get() =
        when (this) {
            PostActionFamily.Reply -> UiIcon.Reply
            PostActionFamily.Comment -> UiIcon.Comment
            PostActionFamily.Repost -> UiIcon.Retweet
            PostActionFamily.Quote -> UiIcon.Quote
            PostActionFamily.Like -> UiIcon.Like
            PostActionFamily.React -> UiIcon.React
            PostActionFamily.Translate -> UiIcon.Translate
            PostActionFamily.Bookmark -> UiIcon.Bookmark
            PostActionFamily.Favorite -> UiIcon.Favourite
            PostActionFamily.Share -> UiIcon.Share
            PostActionFamily.FxShare -> UiIcon.Share
            PostActionFamily.Delete -> UiIcon.Delete
            PostActionFamily.Report -> UiIcon.Report
            PostActionFamily.MuteUser -> UiIcon.Mute
            PostActionFamily.BlockUser -> UiIcon.Block
        }

private val PostActionFamily.label: StringResource
    get() =
        when (this) {
            PostActionFamily.Reply -> Res.string.settings_post_action_family_reply
            PostActionFamily.Comment -> Res.string.settings_post_action_family_comment
            PostActionFamily.Repost -> Res.string.settings_post_action_family_repost
            PostActionFamily.Quote -> Res.string.settings_post_action_family_quote
            PostActionFamily.Like -> Res.string.settings_post_action_family_like
            PostActionFamily.React -> Res.string.settings_post_action_family_react
            PostActionFamily.Translate -> Res.string.settings_post_action_family_translate
            PostActionFamily.Bookmark -> Res.string.settings_post_action_family_bookmark
            PostActionFamily.Favorite -> Res.string.settings_post_action_family_favorite
            PostActionFamily.Share -> Res.string.settings_post_action_family_share
            PostActionFamily.FxShare -> Res.string.settings_post_action_family_fx_share
            PostActionFamily.Delete -> Res.string.settings_post_action_family_delete
            PostActionFamily.Report -> Res.string.settings_post_action_family_report
            PostActionFamily.MuteUser -> Res.string.settings_post_action_family_mute_user
            PostActionFamily.BlockUser -> Res.string.settings_post_action_family_block_user
        }
