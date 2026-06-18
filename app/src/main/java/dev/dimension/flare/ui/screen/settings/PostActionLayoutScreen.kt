package dev.dimension.flare.ui.screen.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bars
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import dev.dimension.flare.R
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.datasource.microblog.PostActionLayoutConfig
import dev.dimension.flare.data.model.appearance.AppearanceKeys
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.component.toImageVector
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.theme.first
import dev.dimension.flare.ui.theme.last
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.theme.segmentedShapes2
import dev.dimension.flare.ui.theme.single
import kotlinx.collections.immutable.toPersistentList
import moe.tlaster.precompose.molecule.producePresenter
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PostActionLayoutScreen(onBack: () -> Unit) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter { appearancePresenter() }
    val timelineAppearance = LocalTimelineAppearance.current
    val persistedConfig = timelineAppearance.postActionLayout.normalizedForEdit()
    val configState = remember { mutableStateOf(persistedConfig) }
    var config by configState
    var isDraggingAction by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(persistedConfig) {
        if (!isDraggingAction) {
            configState.value = persistedConfig
        }
    }

    fun updateConfig(
        value: PostActionLayoutConfig,
        persist: Boolean = true,
    ) {
        val normalized = value.normalizedForEdit()
        configState.value = normalized
        if (persist && normalized != persistedConfig) {
            state.update(AppearanceKeys.PostActionLayout, normalized)
        }
    }

    fun commitConfig() {
        updateConfig(configState.value, persist = true)
    }

    val reorderableState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromFamily = from.key.asPostActionFamilyOrNull() ?: return@rememberReorderableLazyListState
            val toFamily = to.key.asPostActionFamilyOrNull() ?: return@rememberReorderableLazyListState
            val currentConfig = configState.value
            val fromPlacement = currentConfig.placementOf(fromFamily)
            val toPlacement = currentConfig.placementOf(toFamily)
            if (fromPlacement == toPlacement) {
                updateConfig(
                    currentConfig.moveWithin(fromPlacement, fromFamily, toFamily),
                    persist = false,
                )
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }

    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_post_action_layout_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        LazyColumn(
            state = lazyListState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            modifier = Modifier.padding(horizontal = screenHorizontalPadding),
        ) {
            item(key = "preview_and_enabled") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    val hasPreview = state.sampleStatus.isSuccess
                    state.sampleStatus.onSuccess { sample ->
                        SegmentedListItem(
                            onClick = {},
                            shapes = ListItemDefaults.first(),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            CompositionLocalProvider(
                                LocalTimelineAppearance provides timelineAppearance.copy(postActionLayout = config),
                            ) {
                                StatusItem(
                                    sample.withPostActionLayoutPreviewActions(),
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                                )
                            }
                        }
                    }

                    SegmentedListItem(
                        onClick = {
                            updateConfig(config.copy(enabled = !config.enabled))
                        },
                        shapes = if (hasPreview) ListItemDefaults.last() else ListItemDefaults.single(),
                        content = {
                            Text(text = stringResource(id = R.string.settings_post_action_layout_enable))
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_post_action_layout_enable_description))
                        },
                        trailingContent = {
                            Switch(
                                checked = config.enabled,
                                onCheckedChange = {
                                    updateConfig(config.copy(enabled = it))
                                },
                            )
                        },
                    )
                }
            }

            if (config.enabled) {
                postActionSection(
                    titleRes = R.string.settings_post_action_layout_button_row,
                    placement = PostActionPlacement.ButtonRow,
                    config = config,
                    onConfigChanged = {
                        updateConfig(it)
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
                    titleRes = R.string.settings_post_action_layout_more_menu,
                    placement = PostActionPlacement.MoreMenu,
                    config = config,
                    onConfigChanged = {
                        updateConfig(it)
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
                    titleRes = R.string.settings_post_action_layout_hidden,
                    placement = PostActionPlacement.Hidden,
                    config = config,
                    onConfigChanged = {
                        updateConfig(it)
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
    titleRes: Int,
    placement: PostActionPlacement,
    config: PostActionLayoutConfig,
    onConfigChanged: (PostActionLayoutConfig) -> Unit,
    onDragStarted: () -> Unit,
    onDragStopped: () -> Unit,
    reorderableState: sh.calvin.reorderable.ReorderableLazyListState,
) {
    val families = config.familiesFor(placement)
    item(key = "${placement.name}_title") {
        Text(
            text = stringResource(id = titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier =
                Modifier
                    .padding(top = 16.dp)
                    .padding(horizontal = 16.dp),
        )
    }
    if (families.isEmpty()) {
        item(key = "${placement.name}_empty") {
            SegmentedListItem(
                onClick = {},
                shapes = ListItemDefaults.single(),
                content = {
                    Text(text = stringResource(id = R.string.settings_post_action_layout_empty))
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    reorderableState: sh.calvin.reorderable.ReorderableLazyListState,
) {
    val haptics = LocalHapticFeedback.current
    val key = family.saveableKey
    ReorderableItem(reorderableState, key = key) { isDragging ->
        val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
        var showMenu by remember { mutableStateOf(false) }
        SegmentedListItem(
            elevation = ListItemDefaults.elevation(elevation),
            selected = isDragging,
            onClick = {
                showMenu = true
            },
            shapes = ListItemDefaults.segmentedShapes2(index, totalCount),
            leadingContent = {
                FAIcon(
                    imageVector = family.icon.toImageVector(),
                    contentDescription = null,
                )
            },
            content = {
                Text(text = stringResource(id = family.labelRes))
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        modifier =
                            Modifier.draggableHandle(
                                onDragStarted = {
                                    onDragStarted()
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                },
                                onDragStopped = {
                                    onDragStopped()
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                },
                            ),
                        onClick = {},
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Bars,
                            contentDescription = stringResource(id = R.string.tab_settings_drag),
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.EllipsisVertical,
                                contentDescription = stringResource(id = R.string.more),
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            enumValues<PostActionPlacement>().forEach { targetPlacement ->
                                if (targetPlacement != placement) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(text = stringResource(id = targetPlacement.moveLabelRes))
                                        },
                                        onClick = {
                                            showMenu = false
                                            onConfigChanged(config.moveTo(family, targetPlacement))
                                        },
                                    )
                                }
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(text = stringResource(id = R.string.settings_post_action_layout_move_up))
                                },
                                enabled = index > 0,
                                onClick = {
                                    showMenu = false
                                    onConfigChanged(config.moveBy(family, -1))
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(text = stringResource(id = R.string.settings_post_action_layout_move_down))
                                },
                                enabled = index < totalCount - 1,
                                onClick = {
                                    showMenu = false
                                    onConfigChanged(config.moveBy(family, 1))
                                },
                            )
                        }
                    }
                }
            },
        )
    }
}

private fun UiTimelineV2.Post.withPostActionLayoutPreviewActions(): UiTimelineV2.Post {
    var replacedMore = false
    val previewActions =
        actions
            .map { action ->
                val item = action as? ActionMenu.Item
                if (item?.isMoreMenuDisplayItem() == true) {
                    replacedMore = true
                    previewMoreGroup(item)
                } else if (item?.actionFamily == PostActionFamily.Repost) {
                    previewRepostGroup(item)
                } else {
                    action
                }
            }.let { actions ->
                if (replacedMore) actions else actions + previewMoreGroup()
            }
    return copy(actions = previewActions.toPersistentList())
}

private fun ActionMenu.Item.isMoreMenuDisplayItem(): Boolean =
    actionFamily == null &&
        icon == UiIcon.More &&
        text == ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More)

private fun previewMoreGroup(
    displayItem: ActionMenu.Item =
        ActionMenu.Item(
            icon = UiIcon.More,
            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
        ),
): ActionMenu.Group =
    ActionMenu.Group(
        displayItem = displayItem,
        actions =
            listOf(
                ActionMenu.Item(
                    icon = UiIcon.Translate,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Translate),
                    actionFamily = PostActionFamily.Translate,
                ),
                ActionMenu.Item(
                    icon = UiIcon.Bookmark,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Bookmark),
                    count = UiNumber(4),
                    actionFamily = PostActionFamily.Bookmark,
                ),
                ActionMenu.Item(
                    icon = UiIcon.Share,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                    actionFamily = PostActionFamily.Share,
                ),
            ).toPersistentList(),
    )

private fun previewRepostGroup(displayItem: ActionMenu.Item): ActionMenu.Group =
    ActionMenu.Group(
        displayItem = displayItem,
        actions =
            listOf(
                displayItem,
                ActionMenu.Item(
                    icon = UiIcon.Quote,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Quote),
                    count = UiNumber(2),
                    actionFamily = PostActionFamily.Quote,
                ),
            ).toPersistentList(),
    )

private enum class PostActionPlacement {
    ButtonRow,
    MoreMenu,
    Hidden,
}

private const val POST_ACTION_FAMILY_KEY_PREFIX = "post_action_family:"

private val PostActionFamily.saveableKey: String
    get() = "$POST_ACTION_FAMILY_KEY_PREFIX$name"

private fun Any?.asPostActionFamilyOrNull(): PostActionFamily? {
    val key = this as? String ?: return null
    if (!key.startsWith(POST_ACTION_FAMILY_KEY_PREFIX)) return null
    val name = key.removePrefix(POST_ACTION_FAMILY_KEY_PREFIX)
    return runCatching { enumValueOf<PostActionFamily>(name) }.getOrNull()
}

private val allPostActionFamilies: List<PostActionFamily> =
    listOf(
        PostActionFamily.Reply,
        PostActionFamily.Comment,
        PostActionFamily.Repost,
        PostActionFamily.Like,
        PostActionFamily.React,
        PostActionFamily.Translate,
        PostActionFamily.Bookmark,
        PostActionFamily.Favorite,
        PostActionFamily.Share,
        PostActionFamily.Delete,
        PostActionFamily.Report,
        PostActionFamily.MuteUser,
        PostActionFamily.BlockUser,
    )

private fun PostActionLayoutConfig.normalizedForEdit(): PostActionLayoutConfig {
    val primary = primary.cleanFamilies()
    val hidden = hidden.cleanFamilies().filterNot { it in primary }
    val overflow =
        (
            overflow.cleanFamilies().filterNot { it in primary || it in hidden } +
                allPostActionFamilies.filterNot { it in primary || it in hidden || it in overflow }
        ).distinct()
    return copy(
        primary = primary.toPersistentList(),
        overflow = overflow.toPersistentList(),
        hidden = hidden.toPersistentList(),
    )
}

private fun Iterable<PostActionFamily>.cleanFamilies(): List<PostActionFamily> = filter { it in allPostActionFamilies }.distinct()

private fun PostActionLayoutConfig.familiesFor(placement: PostActionPlacement): List<PostActionFamily> =
    when (placement) {
        PostActionPlacement.ButtonRow -> primary.toList()
        PostActionPlacement.MoreMenu -> overflow.toList()
        PostActionPlacement.Hidden -> hidden.toList()
    }

private fun PostActionLayoutConfig.placementOf(family: PostActionFamily): PostActionPlacement =
    when {
        family in primary -> PostActionPlacement.ButtonRow
        family in hidden -> PostActionPlacement.Hidden
        else -> PostActionPlacement.MoreMenu
    }

private fun PostActionLayoutConfig.moveTo(
    family: PostActionFamily,
    placement: PostActionPlacement,
): PostActionLayoutConfig {
    val primary = primary.filterNot { it == family }.toMutableList()
    val overflow = overflow.filterNot { it == family }.toMutableList()
    val hidden = hidden.filterNot { it == family }.toMutableList()
    when (placement) {
        PostActionPlacement.ButtonRow -> primary += family
        PostActionPlacement.MoreMenu -> overflow += family
        PostActionPlacement.Hidden -> hidden += family
    }
    return copy(
        primary = primary.toPersistentList(),
        overflow = overflow.toPersistentList(),
        hidden = hidden.toPersistentList(),
    ).normalizedForEdit()
}

private fun PostActionLayoutConfig.moveWithin(
    placement: PostActionPlacement,
    from: PostActionFamily,
    to: PostActionFamily,
): PostActionLayoutConfig {
    if (from == to) return this
    return when (placement) {
        PostActionPlacement.ButtonRow -> copy(primary = primary.toMutableList().move(from, to).toPersistentList())
        PostActionPlacement.MoreMenu -> copy(overflow = overflow.toMutableList().move(from, to).toPersistentList())
        PostActionPlacement.Hidden -> copy(hidden = hidden.toMutableList().move(from, to).toPersistentList())
    }.normalizedForEdit()
}

private fun PostActionLayoutConfig.moveBy(
    family: PostActionFamily,
    offset: Int,
): PostActionLayoutConfig {
    val placement = placementOf(family)
    val families = familiesFor(placement)
    val fromIndex = families.indexOf(family)
    if (fromIndex == -1) return this
    val toIndex = (fromIndex + offset).coerceIn(families.indices)
    if (fromIndex == toIndex) return this
    return moveWithin(placement, family, families[toIndex])
}

private fun MutableList<PostActionFamily>.move(
    from: PostActionFamily,
    to: PostActionFamily,
): MutableList<PostActionFamily> {
    val fromIndex = indexOf(from)
    val toIndex = indexOf(to)
    if (fromIndex == -1 || toIndex == -1) return this
    add(toIndex, removeAt(fromIndex))
    return this
}

private val PostActionPlacement.moveLabelRes: Int
    get() =
        when (this) {
            PostActionPlacement.ButtonRow -> R.string.settings_post_action_layout_move_to_button_row
            PostActionPlacement.MoreMenu -> R.string.settings_post_action_layout_move_to_more_menu
            PostActionPlacement.Hidden -> R.string.settings_post_action_layout_hide_action
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

private val PostActionFamily.labelRes: Int
    get() =
        when (this) {
            PostActionFamily.Reply -> R.string.settings_post_action_family_reply
            PostActionFamily.Comment -> R.string.settings_post_action_family_comment
            PostActionFamily.Repost -> R.string.settings_post_action_family_repost
            PostActionFamily.Quote -> R.string.settings_post_action_family_quote
            PostActionFamily.Like -> R.string.settings_post_action_family_like
            PostActionFamily.React -> R.string.settings_post_action_family_react
            PostActionFamily.Translate -> R.string.settings_post_action_family_translate
            PostActionFamily.Bookmark -> R.string.settings_post_action_family_bookmark
            PostActionFamily.Favorite -> R.string.settings_post_action_family_favorite
            PostActionFamily.Share -> R.string.settings_post_action_family_share
            PostActionFamily.FxShare -> R.string.settings_post_action_family_fx_share
            PostActionFamily.Delete -> R.string.settings_post_action_family_delete
            PostActionFamily.Report -> R.string.settings_post_action_family_report
            PostActionFamily.MuteUser -> R.string.settings_post_action_family_mute_user
            PostActionFamily.BlockUser -> R.string.settings_post_action_family_block_user
        }
