package dev.dimension.flare.data.datasource.microblog

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.SerializableImmutableList
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.onClicked
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.native.HiddenFromObjC

@Serializable
@Immutable
public enum class PostActionFamily {
    Reply,
    Comment,
    Repost,
    Quote,
    Like,
    React,
    Translate,
    Bookmark,
    Favorite,
    Share,
    FxShare,
    Delete,
    Report,
    MuteUser,
    BlockUser,
}

public enum class PostActionPlacement {
    ButtonRow,
    MoreMenu,
    Hidden,
}

@Serializable
@Immutable
public data class PostActionLayoutConfig(
    val enabled: Boolean = false,
    val primary: SerializableImmutableList<PostActionFamily> = DefaultPrimary,
    val overflow: SerializableImmutableList<PostActionFamily> = DefaultOverflow,
    val hidden: SerializableImmutableList<PostActionFamily> = persistentListOf(),
) {
    public companion object {
        public val DefaultPrimary: SerializableImmutableList<PostActionFamily> =
            persistentListOf(
                PostActionFamily.Reply,
                PostActionFamily.Comment,
                PostActionFamily.Repost,
                PostActionFamily.Like,
                PostActionFamily.React,
            )

        public val DefaultOverflow: SerializableImmutableList<PostActionFamily> =
            persistentListOf(
                PostActionFamily.Translate,
                PostActionFamily.Bookmark,
                PostActionFamily.Favorite,
                PostActionFamily.Share,
                PostActionFamily.Delete,
                PostActionFamily.Report,
                PostActionFamily.MuteUser,
                PostActionFamily.BlockUser,
            )

        public val Default: PostActionLayoutConfig = PostActionLayoutConfig()
    }
}

@Serializable
@Immutable
public sealed class ActionMenu {
    @Immutable
    @Serializable
    public data class Group public constructor(
        val displayItem: Item,
        val actions: SerializableImmutableList<ActionMenu>,
    ) : ActionMenu()

//    @Immutable
//    @Serializable
//    public data class AsyncActionMenuItem internal constructor(
//        @Transient
//        val flow: Flow<Item> = emptyFlow(),
//    ) : ActionMenu()

    @Immutable
    @Serializable
    public data object Divider : ActionMenu()

    @Immutable
    @Serializable
    public data class Item public constructor(
        public val updateKey: String = "",
        val icon: UiIcon? = null,
        val text: Text? = null,
        val count: UiNumber? = null,
        val color: Color? = null,
        public val clickEvent: ClickEvent = ClickEvent.Noop,
        val actionFamily: PostActionFamily? = null,
    ) : ActionMenu() {
        init {
            require(icon != null || text != null) {
                "icon and text cannot be both null"
            }
        }

        val onClicked: ClickContext.() -> Unit by lazy {
            clickEvent.onClicked
        }

        @Serializable
        public enum class Color {
            Red,
            ContentColor,
            PrimaryColor,
        }

        @Immutable
        @Serializable
        public sealed interface Text {
            @Immutable
            @Serializable
            public data class Raw(
                val text: String,
            ) : Text

            @Immutable
            @Serializable
            public data class Localized(
                @SerialName("action_type")
                val type: Type,
                val parameters: SerializableImmutableList<String> = persistentListOf(),
            ) : Text {
                @Serializable
                public enum class Type {
                    Like,
                    Unlike,
                    Retweet,
                    Unretweet,
                    Reply,
                    Comment,
                    Quote,
                    Bookmark,
                    Unbookmark,
                    More,
                    Delete,
                    Report,
                    React,
                    UnReact,
                    Share,
                    FxShare,
                    EditUserList,
                    SendMessage,
                    Mute,
                    UnMute,
                    Block,
                    UnBlock,
                    BlockWithHandleParameter,
                    MuteWithHandleParameter,
                    AcceptFollowRequest,
                    RejectFollowRequest,
                    RetryTranslation,
                    Translate,
                    ShowOriginal,
                    Favorite,
                    UnFavorite,
                }
            }
        }
    }
}

@HiddenFromObjC
public fun ImmutableList<ActionMenu>.applyPostActionLayout(config: PostActionLayoutConfig): ImmutableList<ActionMenu> {
    if (!config.enabled) return this

    var nextIndex = 0
    val entries = mutableListOf<PostActionEntry>()

    fun collect(action: ActionMenu) {
        when (action) {
            is ActionMenu.Item -> {
                if (!action.isDisplayOnlyPostActionContainer()) {
                    entries += PostActionEntry(nextIndex++, action, action.actionFamily)
                }
            }

            is ActionMenu.Group -> {
                if (action.displayItem.isDisplayOnlyPostActionContainer()) {
                    action.actions.forEach(::collect)
                } else {
                    entries += PostActionEntry(nextIndex++, action, action.actionFamilyForLayout())
                }
            }

            ActionMenu.Divider -> {}
        }
    }

    forEach(::collect)

    val consumedIndexes = mutableSetOf<Int>()
    val hiddenFamilies = config.hidden.toSet()

    fun pick(family: PostActionFamily): ActionMenu? =
        entries
            .firstOrNull {
                it.family == family &&
                    it.index !in consumedIndexes &&
                    it.family !in hiddenFamilies
            }?.also {
                consumedIndexes += it.index
            }?.action

    val primaryFamilies = config.primary.distinct().filterNot { it in hiddenFamilies }
    val overflowFamilies =
        config.overflow
            .distinct()
            .filterNot { it in hiddenFamilies }
            .filterNot { it in primaryFamilies }

    val primaryActions = primaryFamilies.mapNotNull(::pick).toMutableList<ActionMenu>()
    val overflowActions = overflowFamilies.mapNotNull(::pick).toMutableList<ActionMenu>()
    overflowActions +=
        entries
            .filterNot { it.index in consumedIndexes }
            .filterNot { it.family?.let { family -> family in hiddenFamilies } == true }
            .map { it.action }

    val normalizedOverflow = overflowActions.normalizePostActionDividers()
    val displayActions =
        if (normalizedOverflow.isEmpty()) {
            primaryActions
        } else {
            primaryActions +
                ActionMenu.Group(
                    displayItem =
                        ActionMenu.Item(
                            icon = UiIcon.More,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                        ),
                    actions = normalizedOverflow.toPersistentList(),
                )
        }

    return displayActions.toPersistentList()
}

public object PostActionLayoutHelpers {
    public val allEditableFamilies: List<PostActionFamily> =
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

    public fun normalizedForEdit(config: PostActionLayoutConfig): PostActionLayoutConfig {
        val primary = config.primary.cleanFamilies()
        val hidden = config.hidden.cleanFamilies().filterNot { it in primary }
        val overflow =
            (
                config.overflow.cleanFamilies().filterNot { it in primary || it in hidden } +
                    allEditableFamilies.filterNot { it in primary || it in hidden || it in config.overflow }
            ).distinct()
        return config.copy(
            primary = primary.toPersistentList(),
            overflow = overflow.toPersistentList(),
            hidden = hidden.toPersistentList(),
        )
    }

    public fun withEnabled(
        config: PostActionLayoutConfig,
        enabled: Boolean,
    ): PostActionLayoutConfig =
        normalizedForEdit(
            config.copy(enabled = enabled),
        )

    public fun familiesFor(
        config: PostActionLayoutConfig,
        placement: PostActionPlacement,
    ): List<PostActionFamily> =
        when (placement) {
            PostActionPlacement.ButtonRow -> config.primary.toList()
            PostActionPlacement.MoreMenu -> config.overflow.toList()
            PostActionPlacement.Hidden -> config.hidden.toList()
        }

    public fun placementOf(
        config: PostActionLayoutConfig,
        family: PostActionFamily,
    ): PostActionPlacement =
        when {
            family in config.primary -> PostActionPlacement.ButtonRow
            family in config.hidden -> PostActionPlacement.Hidden
            else -> PostActionPlacement.MoreMenu
        }

    public fun moveTo(
        config: PostActionLayoutConfig,
        family: PostActionFamily,
        placement: PostActionPlacement,
    ): PostActionLayoutConfig {
        val primary = config.primary.filterNot { it == family }.toMutableList()
        val overflow = config.overflow.filterNot { it == family }.toMutableList()
        val hidden = config.hidden.filterNot { it == family }.toMutableList()
        when (placement) {
            PostActionPlacement.ButtonRow -> primary += family
            PostActionPlacement.MoreMenu -> overflow += family
            PostActionPlacement.Hidden -> hidden += family
        }
        return normalizedForEdit(
            config.copy(
                primary = primary.toPersistentList(),
                overflow = overflow.toPersistentList(),
                hidden = hidden.toPersistentList(),
            ),
        )
    }

    public fun moveWithin(
        config: PostActionLayoutConfig,
        placement: PostActionPlacement,
        from: PostActionFamily,
        to: PostActionFamily,
    ): PostActionLayoutConfig {
        if (from == to) return config
        return when (placement) {
            PostActionPlacement.ButtonRow -> {
                config.copy(
                    primary =
                        config.primary
                            .toMutableList()
                            .move(from, to)
                            .toPersistentList(),
                )
            }

            PostActionPlacement.MoreMenu -> {
                config.copy(
                    overflow =
                        config.overflow
                            .toMutableList()
                            .move(from, to)
                            .toPersistentList(),
                )
            }

            PostActionPlacement.Hidden -> {
                config.copy(
                    hidden =
                        config.hidden
                            .toMutableList()
                            .move(from, to)
                            .toPersistentList(),
                )
            }
        }.let(::normalizedForEdit)
    }

    public fun moveAt(
        config: PostActionLayoutConfig,
        placement: PostActionPlacement,
        fromIndex: Int,
        toOffset: Int,
    ): PostActionLayoutConfig {
        val families = familiesFor(config, placement).toMutableList()
        if (fromIndex !in families.indices) return config
        val item = families.removeAt(fromIndex)
        val destination = if (toOffset > fromIndex) toOffset - 1 else toOffset
        families.add(destination.coerceIn(0, families.size), item)
        return replaceFamilies(config, placement, families)
    }

    public fun moveBy(
        config: PostActionLayoutConfig,
        family: PostActionFamily,
        offset: Int,
    ): PostActionLayoutConfig {
        val placement = placementOf(config, family)
        val families = familiesFor(config, placement)
        val fromIndex = families.indexOf(family)
        if (fromIndex == -1) return config
        val toIndex = (fromIndex + offset).coerceIn(families.indices)
        if (fromIndex == toIndex) return config
        return moveWithin(config, placement, family, families[toIndex])
    }

    public fun apply(
        actions: List<ActionMenu>,
        config: PostActionLayoutConfig,
    ): List<ActionMenu> =
        actions
            .toPersistentList()
            .applyPostActionLayout(config)
            .toList()

    public fun signature(config: PostActionLayoutConfig): String =
        buildString {
            append(config.enabled)
            append('|')
            append(config.primary.joinToString(",") { it.name })
            append('|')
            append(config.overflow.joinToString(",") { it.name })
            append('|')
            append(config.hidden.joinToString(",") { it.name })
        }

    private fun replaceFamilies(
        config: PostActionLayoutConfig,
        placement: PostActionPlacement,
        families: List<PostActionFamily>,
    ): PostActionLayoutConfig =
        when (placement) {
            PostActionPlacement.ButtonRow -> config.copy(primary = families.toPersistentList())
            PostActionPlacement.MoreMenu -> config.copy(overflow = families.toPersistentList())
            PostActionPlacement.Hidden -> config.copy(hidden = families.toPersistentList())
        }.let(::normalizedForEdit)
}

private data class PostActionEntry(
    val index: Int,
    val action: ActionMenu,
    val family: PostActionFamily?,
)

private fun Iterable<PostActionFamily>.cleanFamilies(): List<PostActionFamily> =
    filter { it in PostActionLayoutHelpers.allEditableFamilies }.distinct()

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

private fun ActionMenu.Group.actionFamilyForLayout(): PostActionFamily? =
    displayItem.actionFamily
        ?: actions
            .asSequence()
            .filterIsInstance<ActionMenu.Item>()
            .mapNotNull { it.actionFamily }
            .firstOrNull { it == PostActionFamily.Repost }
        ?: actions
            .asSequence()
            .filterIsInstance<ActionMenu.Item>()
            .mapNotNull { it.actionFamily }
            .firstOrNull()

private fun List<ActionMenu>.normalizePostActionDividers(): List<ActionMenu> {
    val result = mutableListOf<ActionMenu>()
    var lastWasDivider = true
    for (action in this) {
        when (action) {
            ActionMenu.Divider -> {
                if (!lastWasDivider) {
                    result += action
                    lastWasDivider = true
                }
            }

            else -> {
                result += action
                lastWasDivider = false
            }
        }
    }
    while (result.lastOrNull() == ActionMenu.Divider) {
        result.removeAt(result.lastIndex)
    }
    return result
}

private fun ActionMenu.Item.isDisplayOnlyPostActionContainer(): Boolean =
    actionFamily == null &&
        clickEvent == ClickEvent.Noop &&
        icon == UiIcon.More &&
        text == ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More)

@HiddenFromObjC
public fun userActionsMenu(
    accountKey: MicroBlogKey?,
    userKey: MicroBlogKey,
    handle: String,
): List<ActionMenu> =
    listOfNotNull(
        ActionMenu.Item(
            icon = UiIcon.Mute,
            text =
                ActionMenu.Item.Text.Localized(
                    type = ActionMenu.Item.Text.Localized.Type.MuteWithHandleParameter,
                    parameters = persistentListOf(handle),
                ),
            clickEvent =
                ClickEvent.Deeplink(
                    DeeplinkRoute.MuteUser(accountKey, userKey),
                ),
            actionFamily = PostActionFamily.MuteUser,
        ),
        ActionMenu.Item(
            icon = UiIcon.Block,
            text =
                ActionMenu.Item.Text.Localized(
                    type = ActionMenu.Item.Text.Localized.Type.BlockWithHandleParameter,
                    parameters = persistentListOf(handle),
                ),
            clickEvent =
                ClickEvent.Deeplink(
                    DeeplinkRoute.BlockUser(accountKey, userKey),
                ),
            actionFamily = PostActionFamily.BlockUser,
        ),
    )
