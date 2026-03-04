package dev.dimension.flare.ui.model.mapper

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.userActionsMenu
import dev.dimension.flare.data.network.misskey.api.model.Antenna
import dev.dimension.flare.data.network.misskey.api.model.Channel
import dev.dimension.flare.data.network.misskey.api.model.DriveFile
import dev.dimension.flare.data.network.misskey.api.model.EmojiSimple
import dev.dimension.flare.data.network.misskey.api.model.Meta200Response
import dev.dimension.flare.data.network.misskey.api.model.Note
import dev.dimension.flare.data.network.misskey.api.model.Notification
import dev.dimension.flare.data.network.misskey.api.model.NotificationType
import dev.dimension.flare.data.network.misskey.api.model.User
import dev.dimension.flare.data.network.misskey.api.model.UserList
import dev.dimension.flare.data.network.misskey.api.model.UserLite
import dev.dimension.flare.data.network.misskey.api.model.Visibility
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.toAccountType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiPoll
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentList
import moe.tlaster.mfm.parser.MFMParser
import moe.tlaster.mfm.parser.tree.BoldNode
import moe.tlaster.mfm.parser.tree.CashNode
import moe.tlaster.mfm.parser.tree.CenterNode
import moe.tlaster.mfm.parser.tree.CodeBlockNode
import moe.tlaster.mfm.parser.tree.EmojiCodeNode
import moe.tlaster.mfm.parser.tree.FnNode
import moe.tlaster.mfm.parser.tree.HashtagNode
import moe.tlaster.mfm.parser.tree.InlineCodeNode
import moe.tlaster.mfm.parser.tree.ItalicNode
import moe.tlaster.mfm.parser.tree.LinkNode
import moe.tlaster.mfm.parser.tree.MathBlockNode
import moe.tlaster.mfm.parser.tree.MathInlineNode
import moe.tlaster.mfm.parser.tree.MentionNode
import moe.tlaster.mfm.parser.tree.QuoteNode
import moe.tlaster.mfm.parser.tree.RootNode
import moe.tlaster.mfm.parser.tree.SearchNode
import moe.tlaster.mfm.parser.tree.SmallNode
import moe.tlaster.mfm.parser.tree.StrikeNode
import moe.tlaster.mfm.parser.tree.UrlNode
import kotlin.math.roundToLong
import kotlin.time.Instant

internal fun List<Note>.render(accountKey: MicroBlogKey): List<UiTimelineV2> = map { it.render(accountKey) }

internal fun List<Notification>.render(accountKey: MicroBlogKey): List<UiTimelineV2> = map { it.render(accountKey) }

internal fun Notification.render(accountKey: MicroBlogKey): UiTimelineV2 {
    val user = user?.render(accountKey)
    val notificationType =
        runCatching {
            NotificationType.entries.first { it.value == type }
        }.getOrNull()
    val createdAt = Instant.parse(createdAt).toUi()
    val statusKey = MicroBlogKey(id, accountKey.host)
    val messageType =
        when (notificationType) {
            NotificationType.Follow ->
                UiTimelineV2.Message.Type.Localized(UiTimelineV2.Message.Type.Localized.MessageId.Follow)
            NotificationType.Mention ->
                UiTimelineV2.Message.Type.Localized(UiTimelineV2.Message.Type.Localized.MessageId.Mention)
            NotificationType.Reply ->
                UiTimelineV2.Message.Type.Localized(UiTimelineV2.Message.Type.Localized.MessageId.Reply)
            NotificationType.Renote ->
                UiTimelineV2.Message.Type.Localized(UiTimelineV2.Message.Type.Localized.MessageId.Repost)
            NotificationType.Quote ->
                UiTimelineV2.Message.Type.Localized(UiTimelineV2.Message.Type.Localized.MessageId.Quote)
            NotificationType.Reaction ->
                UiTimelineV2.Message.Type.Localized(UiTimelineV2.Message.Type.Localized.MessageId.Reaction)
            NotificationType.PollEnded ->
                UiTimelineV2.Message.Type.Localized(UiTimelineV2.Message.Type.Localized.MessageId.PollEnded)
            NotificationType.ReceiveFollowRequest ->
                UiTimelineV2.Message.Type.Localized(UiTimelineV2.Message.Type.Localized.MessageId.FollowRequest)
            NotificationType.FollowRequestAccepted ->
                UiTimelineV2.Message.Type.Localized(UiTimelineV2.Message.Type.Localized.MessageId.FollowRequestAccepted)
            NotificationType.AchievementEarned ->
                UiTimelineV2.Message.Type.Localized(
                    UiTimelineV2.Message.Type.Localized.MessageId.AchievementEarned,
                    listOfNotNull(achievement).toPersistentList(),
                )
            NotificationType.App ->
                UiTimelineV2.Message.Type.Localized(UiTimelineV2.Message.Type.Localized.MessageId.App)
            else -> UiTimelineV2.Message.Type.Unknown(rawType = type)
        }
    val message =
        UiTimelineV2.Message(
            user = user,
            icon =
                when (notificationType) {
                    NotificationType.Follow -> UiIcon.Follow
                    NotificationType.Mention -> UiIcon.Mention
                    NotificationType.Reply -> UiIcon.Reply
                    NotificationType.Renote -> UiIcon.Retweet
                    NotificationType.Quote -> UiIcon.Quote
                    NotificationType.Reaction -> UiIcon.Favourite
                    NotificationType.PollEnded -> UiIcon.Poll
                    NotificationType.ReceiveFollowRequest -> UiIcon.Follow
                    NotificationType.FollowRequestAccepted -> UiIcon.Follow
                    NotificationType.AchievementEarned -> UiIcon.Info
                    NotificationType.App -> UiIcon.Info
                    else -> UiIcon.Info
                },
            type = messageType,
            statusKey = statusKey,
            createdAt = createdAt,
            clickEvent =
                user?.let {
                    ClickEvent.Deeplink(
                        DeeplinkRoute.Profile.User(
                            accountType = accountKey.toAccountType(),
                            userKey = it.key,
                        ),
                    )
                } ?: ClickEvent.Noop,
            accountType = accountKey.toAccountType(),
        )

    if (notificationType in listOf(NotificationType.Follow, NotificationType.FollowRequestAccepted) && user != null) {
        return UiTimelineV2.User(
            message = message,
            value = user,
            createdAt = createdAt,
            statusKey = statusKey,
            accountType = accountKey.toAccountType(),
        )
    }

    if (notificationType == NotificationType.ReceiveFollowRequest && user != null) {
        return UiTimelineV2.User(
            message = message,
            value = user,
            button =
                persistentListOf(
                    ActionMenu.Item(
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.AcceptFollowRequest),
                        color = ActionMenu.Item.Color.PrimaryColor,
                        icon = UiIcon.Check,
                        clickEvent =
                            ClickEvent.event(
                                accountKey,
                                PostEvent.Misskey.AcceptFollowRequest(
                                    postKey = statusKey,
                                    userKey = user.key,
                                    notificationStatusKey = statusKey,
                                ),
                            ),
                    ),
                    ActionMenu.Item(
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.RejectFollowRequest),
                        color = ActionMenu.Item.Color.Red,
                        clickEvent =
                            ClickEvent.event(
                                accountKey,
                                PostEvent.Misskey.RejectFollowRequest(
                                    postKey = statusKey,
                                    userKey = user.key,
                                    notificationStatusKey = statusKey,
                                ),
                            ),
                    ),
                ),
            createdAt = createdAt,
            statusKey = statusKey,
            accountType = accountKey.toAccountType(),
        )
    }

    val status =
        note
            ?.let {
                if (notificationType == NotificationType.Renote) {
                    it.renote ?: it
                } else {
                    it
                }
            }?.renderStatus(accountKey)
    return status?.copy(message = message)
        ?: if (user != null) {
            UiTimelineV2.User(
                message = message,
                value = user,
                createdAt = createdAt,
                statusKey = statusKey,
                accountType = accountKey.toAccountType(),
            )
        } else {
            message
        }
}

public enum class MisskeyAchievement(
    internal val id: String,
) {
    NOTES1("notes1"),
    NOTES10("notes10"),
    NOTES100("notes100"),
    NOTES500("notes500"),
    NOTES1000("notes1000"),
    NOTES5000("notes5000"),
    NOTES10000("notes10000"),
    NOTES20000("notes20000"),
    NOTES30000("notes30000"),
    NOTES40000("notes40000"),
    NOTES50000("notes50000"),
    NOTES60000("notes60000"),
    NOTES70000("notes70000"),
    NOTES80000("notes80000"),
    NOTES90000("notes90000"),
    NOTES100000("notes100000"),
    LOGIN3("login3"),
    LOGIN7("login7"),
    LOGIN15("login15"),
    LOGIN30("login30"),
    LOGIN60("login60"),
    LOGIN100("login100"),
    LOGIN200("login200"),
    LOGIN300("login300"),
    LOGIN400("login400"),
    LOGIN500("login500"),
    LOGIN600("login600"),
    LOGIN700("login700"),
    LOGIN800("login800"),
    LOGIN900("login900"),
    LOGIN1000("login1000"),
    NOTE_CLIPPED1("noteClipped1"),
    NOTE_FAVORITED1("noteFavorited1"),
    MY_NOTE_FAVORITED1("myNoteFavorited1"),
    PROFILE_FILLED("profileFilled"),
    MARKED_AS_CAT("markedAsCat"),
    FOLLOWING1("following1"),
    FOLLOWING10("following10"),
    FOLLOWING50("following50"),
    FOLLOWING100("following100"),
    FOLLOWING300("following300"),
    FOLLOWERS1("followers1"),
    FOLLOWERS10("followers10"),
    FOLLOWERS50("followers50"),
    FOLLOWERS100("followers100"),
    FOLLOWERS300("followers300"),
    FOLLOWERS500("followers500"),
    FOLLOWERS1000("followers1000"),
    COLLECT_ACHIEVEMENTS30("collectAchievements30"),
    VIEW_ACHIEVEMENTS3MIN("viewAchievements3min"),
    I_LOVE_MISSKEY("iLoveMisskey"),
    FOUND_TREASURE("foundTreasure"),
    CLIENT30MIN("client30min"),
    CLIENT60MIN("client60min"),
    NOTE_DELETED_WITHIN1MIN("noteDeletedWithin1min"),
    POSTED_AT_LATE_NIGHT("postedAtLateNight"),
    POSTED_AT_0MIN0SEC("postedAt0min0sec"),
    SELF_QUOTE("selfQuote"),
    HTL20NPM("htl20npm"),
    VIEW_INSTANCE_CHART("viewInstanceChart"),
    OUTPUT_HELLO_WORLD_ON_SCRATCHPAD("outputHelloWorldOnScratchpad"),
    OPEN3WINDOWS("open3windows"),
    DRIVE_FOLDER_CIRCULAR_REFERENCE("driveFolderCircularReference"),
    REACT_WITHOUT_READ("reactWithoutRead"),
    CLICKED_CLICK_HERE("clickedClickHere"),
    JUST_PLAIN_LUCKY("justPlainLucky"),
    SET_NAME_TO_SYUILO("setNameToSyuilo"),
    PASSED_SINCE_ACCOUNT_CREATED1("passedSinceAccountCreated1"),
    PASSED_SINCE_ACCOUNT_CREATED2("passedSinceAccountCreated2"),
    PASSED_SINCE_ACCOUNT_CREATED3("passedSinceAccountCreated3"),
    LOGGED_IN_ON_BIRTHDAY("loggedInOnBirthday"),
    LOGGED_IN_ON_NEW_YEARS_DAY("loggedInOnNewYearsDay"),
    COOKIE_CLICKED("cookieClicked"),
    BRAIN_DIVER("brainDiver"),
    SMASH_TEST_NOTIFICATION_BUTTON("smashTestNotificationButton"),
    TUTORIAL_COMPLETED("tutorialCompleted"),
    BUBBLE_GAME_EXPLODING_HEAD("bubbleGameExplodingHead"),
    BUBBLE_GAME_DOUBLE_EXPLODING_HEAD("bubbleGameDoubleExplodingHead"),
    ;

    internal companion object {
        fun fromString(id: String): MisskeyAchievement? = entries.find { it.id == id }
    }
}

internal fun Note.render(accountKey: MicroBlogKey): UiTimelineV2 {
    val actualStatus =
        if (!text.isNullOrEmpty() || !files.isNullOrEmpty() || poll != null || cw != null) {
            this
        } else {
            renote ?: this
        }
    val status = actualStatus.renderStatus(accountKey)
    val topMessage =
        if (actualStatus !== this) {
            val user = user.render(accountKey)
            UiTimelineV2.Message(
                user = user,
                icon = UiIcon.Retweet,
                type = UiTimelineV2.Message.Type.Localized(UiTimelineV2.Message.Type.Localized.MessageId.Repost),
                statusKey = status.statusKey,
                createdAt = status.createdAt,
                clickEvent =
                    ClickEvent.Deeplink(
                        DeeplinkRoute.Profile.User(
                            accountType = accountKey.toAccountType(),
                            userKey = user.key,
                        ),
                    ),
                accountType = accountKey.toAccountType(),
            )
        } else {
            null
        }
    return status.copy(message = topMessage)
}

private fun Note.renderStatus(accountKey: MicroBlogKey): UiTimelineV2.Post {
    val remoteHost =
        if (user.host.isNullOrEmpty()) {
            accountKey.host
        } else {
            user.host
        }
    val parent = reply
    val user = user.render(accountKey)
    val isFromMe = user.key == accountKey
    val canReblog =
        (channel == null || channel.allowRenoteToExternal == null || channel.allowRenoteToExternal) &&
            (
                visibility in listOf(Visibility.Public, Visibility.Home) ||
                    (isFromMe && visibility != Visibility.Specified)
            )
    val renderedVisibility =
        if (channel?.id != null) {
            UiTimelineV2.Post.Visibility.Channel
        } else {
            when (visibility) {
                Visibility.Public -> UiTimelineV2.Post.Visibility.Public
                Visibility.Home -> UiTimelineV2.Post.Visibility.Home
                Visibility.Followers -> UiTimelineV2.Post.Visibility.Followers
                Visibility.Specified -> UiTimelineV2.Post.Visibility.Specified
            }
        }
    val statusKey =
        MicroBlogKey(
            id,
            host = user.key.host,
        )
    val reaction =
        reactions
            .map { emoji ->
                UiTimelineV2.Post.EmojiReaction(
                    name = emoji.key,
                    count = UiNumber(emoji.value),
                    url = resolveMisskeyEmoji(emoji.key, accountKey.host, emojis),
                    isUnicode = !emoji.key.startsWith(':') && !emoji.key.endsWith(':'),
                    clickEvent =
                        ClickEvent.event(
                            accountKey,
                            PostEvent.Misskey.React(
                                postKey = statusKey,
                                hasReacted = myReaction == emoji.key,
                                reaction = emoji.key,
                            ),
                        ),
                    me = myReaction == emoji.key,
                )
            }.sortedByDescending { it.count.value }
            .toImmutableList()
    val sourceChannel =
        if (channel?.id != null && channel.name != null) {
            UiTimelineV2.Post.SourceChannel(
                id = channel.id,
                name = channel.name,
            )
        } else {
            null
        }
    val postUrl =
        buildString {
            if (!url.isNullOrEmpty()) {
                append(url)
            } else if (!uri.isNullOrEmpty()) {
                append(uri)
            } else {
                append("https://")
                append(accountKey.host)
                append("/notes/")
                append(id)
            }
        }
    return UiTimelineV2.Post(
        parents =
            listOfNotNull(
                parent?.renderStatus(accountKey),
            ).toPersistentList(),
        images =
            files
                ?.mapNotNull { file ->
                    file.toUi()
                }?.toPersistentList() ?: persistentListOf(),
        contentWarning =
            if (!cw.isNullOrEmpty() && !text.isNullOrEmpty()) {
                misskeyParser.parse(cw).toHtml(accountKey, emojis, remoteHost).toUi()
            } else {
                null
            },
        user = user,
        quote =
            listOfNotNull(
                if (text != null || !files.isNullOrEmpty() || cw != null || poll != null) {
                    renote?.renderStatus(accountKey)
                } else {
                    null
                },
            ).toImmutableList(),
        content =
            if (!text.isNullOrEmpty()) {
                misskeyParser.parse(text).toHtml(accountKey, emojis, remoteHost).toUi()
            } else if (!cw.isNullOrEmpty()) {
                misskeyParser.parse(cw).toHtml(accountKey, emojis, remoteHost).toUi()
            } else {
                Element("span").toUi()
            },
        actions =
            listOfNotNull(
                ActionMenu.Item(
                    icon = UiIcon.Reply,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
                    count = UiNumber(repliesCount.toLong()),
                    clickEvent =
                        ClickEvent.Deeplink(
                            DeeplinkRoute.Compose
                                .Reply(
                                    accountKey = accountKey,
                                    statusKey = statusKey,
                                ),
                        ),
                ),
                if (canReblog) {
                    ActionMenu.Group(
                        displayItem =
                            ActionMenu.Item(
                                icon = UiIcon.Retweet,
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Retweet),
                                count = UiNumber(renoteCount.toLong()),
                            ),
                        actions =
                            listOfNotNull(
                                ActionMenu.misskeyRenote(
                                    postKey = statusKey,
                                    count = renoteCount.toLong(),
                                    accountKey = accountKey,
                                ),
                                ActionMenu.Item(
                                    icon = UiIcon.Quote,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Quote),
                                    count = UiNumber(0),
                                    clickEvent =
                                        ClickEvent.Deeplink(
                                            DeeplinkRoute.Compose
                                                .Quote(
                                                    accountKey = accountKey,
                                                    statusKey = statusKey,
                                                ),
                                        ),
                                ),
                            ).toImmutableList(),
                    )
                } else {
                    null
                },
                ActionMenu.misskeyReact(
                    postKey = statusKey,
                    hasReacted = myReaction != null,
                    reaction = myReaction,
                    count = reaction.sumOf { it.count.value },
                    accountKey = accountKey,
                ),
                ActionMenu.Group(
                    displayItem =
                        ActionMenu.Item(
                            icon = UiIcon.More,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                        ),
                    actions =
                        buildList {
                            add(
                                ActionMenu.misskeyFavourite(
                                    postKey = statusKey,
                                    favourited = false,
                                    accountKey = accountKey,
                                ),
                            )
                            add(
                                ActionMenu.Item(
                                    icon = UiIcon.Share,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                                    clickEvent =
                                        ClickEvent.Deeplink(
                                            DeeplinkRoute.Status
                                                .ShareSheet(
                                                    statusKey = statusKey,
                                                    accountType = AccountType.Specific(accountKey),
                                                    shareUrl = postUrl,
                                                ),
                                        ),
                                ),
                            )
                            if (isFromMe) {
                                add(
                                    ActionMenu.Item(
                                        icon = UiIcon.Delete,
                                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Delete),
                                        color = ActionMenu.Item.Color.Red,
                                        clickEvent =
                                            ClickEvent.Deeplink(
                                                DeeplinkRoute.Status
                                                    .DeleteConfirm(
                                                        accountType = AccountType.Specific(accountKey),
                                                        statusKey = statusKey,
                                                    ),
                                            ),
                                    ),
                                )
                            } else {
                                add(ActionMenu.Divider)
                                addAll(
                                    userActionsMenu(
                                        accountKey = accountKey,
                                        userKey = user.key,
                                        handle = user.handle,
                                    ),
                                )
                                add(ActionMenu.Divider)
                                add(
                                    ActionMenu.Item(
                                        icon = UiIcon.Report,
                                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Report),
                                        color = ActionMenu.Item.Color.Red,
                                        clickEvent =
                                            ClickEvent.Deeplink(
                                                DeeplinkRoute.Status
                                                    .MisskeyReport(
                                                        statusKey = statusKey,
                                                        userKey = user.key,
                                                        accountType = AccountType.Specific(accountKey),
                                                    ),
                                            ),
                                    ),
                                )
                            }
                        }.toImmutableList(),
                ),
            ).toImmutableList(),
        poll =
            poll?.let {
                UiPoll(
                    // misskey poll doesn't have id
                    id = "",
                    options =
                        poll.choices
                            .map { option ->
                                UiPoll.Option(
                                    title = option.text,
                                    votesCount = option.votes.toLong(),
                                    percentage =
                                        option.votes
                                            .toFloat()
                                            .div(
                                                poll.choices.sumOf { it.votes }.toFloat(),
                                            ).takeUnless { it.isNaN() } ?: 0f,
                                )
                            }.toPersistentList(),
                    expiresAt = poll.expiresAt ?: Instant.DISTANT_PAST,
                    multiple = poll.multiple,
                    ownVotes = List(poll.choices.filter { it.isVoted }.size) { index -> index }.toPersistentList(),
                    voteEvent =
                        PostEvent.Misskey.Vote(
                            accountKey = accountKey,
                            postKey = statusKey,
                            options = persistentListOf(),
                        ),
                    enabled = !isFromMe,
                )
            },
        statusKey = statusKey,
        card = null,
        createdAt = Instant.parse(createdAt).toUi(),
        visibility = renderedVisibility,
        emojiReactions = reaction,
        sourceChannel = sourceChannel,
        sensitive = files?.any { it.isSensitive } ?: false,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Status
                    .Detail(
                        statusKey = statusKey,
                        accountType = AccountType.Specific(accountKey),
                    ),
            ),
        platformType = PlatformType.Misskey,
        accountType = accountKey.toAccountType(),
        replyToHandle =
            parent?.user?.let {
                val host = it.host?.takeIf { host -> host.isNotEmpty() } ?: accountKey.host
                "@${it.username}@$host"
            },
    )
}

internal fun ActionMenu.Companion.misskeyRenote(
    postKey: MicroBlogKey,
    count: Long,
    accountKey: MicroBlogKey?,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "misskey_renote_$postKey",
        icon = UiIcon.Retweet,
        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Retweet),
        count = UiNumber(count),
        clickEvent =
            ClickEvent.event(
                accountKey,
            ) { accountKey ->
                PostEvent.Misskey.Renote(
                    postKey = postKey,
                    count = count,
                    accountKey = accountKey,
                )
            },
    )

internal fun ActionMenu.Companion.misskeyReact(
    postKey: MicroBlogKey,
    hasReacted: Boolean,
    reaction: String?,
    count: Long,
    accountKey: MicroBlogKey?,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "misskey_react_$postKey",
        icon = if (hasReacted) UiIcon.UnReact else UiIcon.React,
        text =
            ActionMenu.Item.Text.Localized(
                if (hasReacted) {
                    ActionMenu.Item.Text.Localized.Type.UnReact
                } else {
                    ActionMenu.Item.Text.Localized.Type.React
                },
            ),
        count = UiNumber(count),
        color = if (hasReacted) ActionMenu.Item.Color.Red else null,
        clickEvent =
            if (!hasReacted || reaction.isNullOrEmpty()) {
                ClickEvent.Deeplink(
                    DeeplinkRoute.Status
                        .AddReaction(
                            statusKey = postKey,
                            accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest,
                        ),
                )
            } else {
                ClickEvent.event(
                    accountKey,
                ) { accountKey ->
                    PostEvent.Misskey.React(
                        postKey = postKey,
                        hasReacted = true,
                        reaction = reaction,
                        count = count,
                        accountKey = accountKey,
                    )
                }
            },
    )

internal fun ActionMenu.Companion.misskeyFavourite(
    postKey: MicroBlogKey,
    favourited: Boolean,
    accountKey: MicroBlogKey?,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "misskey_favourite_$postKey",
        icon = if (favourited) UiIcon.Unlike else UiIcon.Like,
        text =
            ActionMenu.Item.Text.Localized(
                if (favourited) {
                    ActionMenu.Item.Text.Localized.Type.Unlike
                } else {
                    ActionMenu.Item.Text.Localized.Type.Like
                },
            ),
        count = UiNumber(0),
        color = if (favourited) ActionMenu.Item.Color.Red else null,
        clickEvent =
            ClickEvent.event(
                accountKey,
            ) { accountKey ->
                PostEvent.Misskey.Favourite(
                    postKey = postKey,
                    favourited = favourited,
                    accountKey = accountKey,
                )
            },
    )

private fun DriveFile.toUi(): UiMedia? {
    if (type.startsWith("image/")) {
        return UiMedia.Image(
            url = url.orEmpty(),
            // thumbnailUrl is not always present
            previewUrl = thumbnailUrl?.takeIf { it.isNotEmpty() } ?: url.orEmpty(),
            description = comment,
            width = properties.width?.toFloat() ?: 0f,
            height = properties.height?.toFloat() ?: 0f,
            sensitive = isSensitive,
        )
    } else if (type.startsWith("video/")) {
        return UiMedia.Video(
            url = url.orEmpty(),
            // thumbnailUrl is not always present like post from Mastodon
            thumbnailUrl = thumbnailUrl?.takeIf { it.isNotEmpty() } ?: url.orEmpty(),
            description = comment,
            width = properties.width?.toFloat() ?: 0f,
            height = properties.height?.toFloat() ?: 0f,
        )
    } else {
        return null
    }
}

internal fun UserLite.render(accountKey: MicroBlogKey): UiProfile {
    val remoteHost =
        if (host.isNullOrEmpty()) {
            accountKey.host
        } else {
            host
        }
    val userKey =
        MicroBlogKey(
            id = id,
            host = accountKey.host,
        )
    return UiProfile(
        avatar = avatarUrl.orEmpty(),
        nameInternal = parseName(name.orEmpty(), accountKey, emojis).toUi(),
        handle = "@$username@$remoteHost",
        key = userKey,
        banner = null,
        description = null,
        matrices =
            UiProfile.Matrices(
                fansCount = 0,
                followsCount = 0,
                statusesCount = 0,
            ),
        mark = persistentListOf(),
        bottomContent = null,
        platformType = PlatformType.Misskey,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile
                    .User(
                        accountType = AccountType.Specific(accountKey),
                        userKey = userKey,
                    ),
            ),
    )
}

internal fun User.render(accountKey: MicroBlogKey): UiProfile {
    val remoteHost =
        if (host.isNullOrEmpty()) {
            accountKey.host
        } else {
            host
        }
    val userKey =
        MicroBlogKey(
            id = id,
            host = accountKey.host,
        )
    return UiProfile(
        avatar = avatarUrl.orEmpty(),
        nameInternal = parseName(name.orEmpty(), accountKey, emojis).toUi(),
        handle = "@$username@$remoteHost",
        key = userKey,
        banner = bannerUrl,
        description =
            description?.let {
                misskeyParser.parse(it).toHtml(accountKey, emojis, remoteHost).toUi()
            },
        matrices =
            UiProfile.Matrices(
                fansCount = followersCount.toLong(),
                followsCount = followingCount.toLong(),
                statusesCount = notesCount.toLong(),
            ),
        mark =
            listOfNotNull(
                if (isCat == true) {
                    UiProfile.Mark.Cat
                } else {
                    null
                },
                if (isBot == true) {
                    UiProfile.Mark.Bot
                } else {
                    null
                },
            ).toImmutableList(),
        bottomContent =
            fields
                .takeIf {
                    it.any()
                }?.let {
                    UiProfile.BottomContent.Fields(
                        fields =
                            it
                                .associate { (key, value) ->
                                    key to
                                        misskeyParser
                                            .parse(value)
                                            .toHtml(accountKey, emojis, remoteHost)
                                            .toUi()
                                }.toImmutableMap(),
                    )
                },
        platformType = PlatformType.Misskey,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile
                    .User(
                        accountType = AccountType.Specific(accountKey),
                        userKey = userKey,
                    ),
            ),
    )
}

internal fun EmojiSimple.toUi(): UiEmoji =
    UiEmoji(
        shortcode = ":$name:",
        url = url,
        category = category.orEmpty(),
        searchKeywords = (aliases + name).toImmutableList(),
        insertText = " :$name: ",
    )

internal fun resolveMisskeyEmoji(
    name: String,
    accountHost: String,
    emojis: Map<String, String>,
): String =
    name.trim(':').let {
        emojis.getOrElse(it) {
            if (it.endsWith("@.")) {
                "https://$accountHost/emoji/${it.dropLast(2)}.webp"
            } else {
                "https://$accountHost/emoji/$it.webp"
            }
        }
    }

private val misskeyParser by lazy {
    MFMParser()
}

private val misskeyNameParser by lazy {
    MFMParser(emojiOnly = true)
}

private fun parseName(
    name: String,
    accountKey: MicroBlogKey,
    emojis: Map<String, String>,
): Element {
    if (name.isEmpty()) {
        return Element("body")
    }
    return misskeyNameParser.parse(name).toHtml(accountKey, emojis, accountKey.host)
}

private fun moe.tlaster.mfm.parser.tree.Node.toHtml(
    accountKey: MicroBlogKey,
    emojis: Map<String, String>,
    remoteHost: String,
): Element =
    when (this) {
        is CenterNode -> {
            Element("center").apply {
                content.forEach {
                    appendChild(it.toHtml(accountKey, emojis, remoteHost))
                }
            }
        }

        is CodeBlockNode -> {
            Element("pre").apply {
                appendChild(
                    Element("code").apply {
                        language?.let {
//                            attributes["lang"] = it
                            attributes().put("lang", it)
                        }
                        appendChild(TextNode(code))
                    },
                )
            }
        }

        is MathBlockNode -> {
            Element("pre").apply {
                appendChild(
                    Element("code").apply {
//                        attributes["lang"] = "math"
                        attributes().put("lang", "math")
                        appendChild(TextNode(formula))
                    },
                )
            }
        }

        is QuoteNode -> {
            Element("blockquote").apply {
                content.forEach {
                    appendChild(it.toHtml(accountKey, emojis, remoteHost))
                }
            }
        }

        is SearchNode -> {
            Element("search").apply {
                appendChild(TextNode(query))
            }
        }

        is BoldNode -> {
            Element("strong").apply {
                content.forEach {
                    appendChild(it.toHtml(accountKey, emojis, remoteHost))
                }
            }
        }

        is FnNode -> {
            if (name == "unixtime") {
                // for example: 1771316689.8110971
                val time =
                    content
                        .firstOrNull()
                        ?.let { it as? moe.tlaster.mfm.parser.tree.TextNode }
                        ?.content
                        ?.toFloatOrNull()
                if (time != null) {
                    Element("time").apply {
                        appendChild(
                            TextNode(
                                Instant.fromEpochSeconds(time.roundToLong()).toUi().full,
                            ),
                        )
                        attributes().put("datetime", time.toString())
                    }
                } else {
                    Element("span").apply {
                        content.forEach {
                            appendChild(it.toHtml(accountKey, emojis, remoteHost))
                        }
                    }
                }
            } else {
                Element("fn").apply {
//                attributes["name"] = name
                    attributes().put("name", name)
                    content.forEach {
                        appendChild(it.toHtml(accountKey, emojis, remoteHost))
                    }
                }
            }
        }

        is ItalicNode -> {
            Element("em").apply {
                content.forEach {
                    appendChild(it.toHtml(accountKey, emojis, remoteHost))
                }
            }
        }

        is RootNode -> {
            Element("body").apply {
                content.forEach {
                    appendChild(it.toHtml(accountKey, emojis, remoteHost))
                }
            }
        }

        is SmallNode -> {
            Element("small").apply {
                content.forEach {
                    appendChild(it.toHtml(accountKey, emojis, remoteHost))
                }
            }
        }

        is StrikeNode -> {
            Element("s").apply {
                content.forEach {
                    appendChild(it.toHtml(accountKey, emojis, remoteHost))
                }
            }
        }

        is CashNode -> {
            Element("a").apply {
//                attributes["href"] = AppDeepLink.Search(accountKey, "$$content")
                attributes().put(
                    "href",
                    DeeplinkRoute.Search(AccountType.Specific(accountKey), "$$content").toUri(),
                )
                appendChild(TextNode("$$content"))
            }
        }

        is EmojiCodeNode -> {
            Element("img").apply {
//                attributes["src"] = resolveMisskeyEmoji(emoji, accountKey.host)
//                attributes["alt"] = emoji
                attributes().put("src", resolveMisskeyEmoji(emoji, accountKey.host, emojis))
                attributes().put("alt", emoji)
            }
        }

        is HashtagNode -> {
            Element("a").apply {
//                attributes["href"] = AppDeepLink.Search(accountKey, "#$tag")
                attributes().put(
                    "href",
                    DeeplinkRoute.Search(AccountType.Specific(accountKey), "#$tag").toUri(),
                )
                appendChild(TextNode("#$tag"))
            }
        }

        is InlineCodeNode -> {
            Element("code").apply {
                appendChild(TextNode(code))
            }
        }

        is LinkNode -> {
            Element("a").apply {
//                attributes["href"] = url
                attributes().put("href", url)
                content.forEach {
                    appendChild(it.toHtml(accountKey, emojis, remoteHost))
                }
            }
        }

        is MathInlineNode -> {
            Element("code").apply {
//                attributes["lang"] = "math"
                attributes().put("lang", "math")
                appendChild(TextNode(formula))
            }
        }

        is MentionNode -> {
            Element("a").apply {
                val deeplink =
                    DeeplinkRoute.Profile
                        .UserNameWithHost(
                            AccountType.Specific(accountKey),
                            userName,
                            host ?: remoteHost,
                        ).toUri()
//                attributes["href"] = deeplink
                attributes().put("href", deeplink)
                appendChild(
                    TextNode(
                        buildString {
                            append("@")
                            append(userName)
                            if (host != null) {
                                append("@")
                                append(host)
                            }
                        },
                    ),
                )
            }
        }

        is moe.tlaster.mfm.parser.tree.TextNode -> {
            Element("span").apply {
                content.split("\n").forEachIndexed { index, line ->
                    if (index != 0) {
                        appendChild(Element("br"))
                    }
                    appendChild(TextNode(line))
                }
            }
        }

        is UrlNode -> {
            Element("a").apply {
//                attributes["href"] = url
                attributes().put("href", url)
                appendChild(TextNode(url))
            }
        }
    }

internal fun UserList.render(): UiList.List =
    UiList.List(
        id = id,
        title = name,
    )

internal fun Meta200Response.render(): UiInstanceMetadata {
    val configuration =
        UiInstanceMetadata.Configuration(
            registration =
                UiInstanceMetadata.Configuration.Registration(
                    enabled = this.disableRegistration != true,
                ),
            statuses =
                UiInstanceMetadata.Configuration.Statuses(
                    maxCharacters = this.maxNoteTextLength ?: 3000,
                    maxMediaAttachments = 4,
                ),
            mediaAttachment =
                UiInstanceMetadata.Configuration.MediaAttachment(
                    imageSizeLimit = -1,
                    descriptionLimit = 1500,
                    supportedMimeTypes = persistentListOf(),
                ),
            poll =
                UiInstanceMetadata.Configuration.Poll(
                    maxOptions = 4,
                    maxCharactersPerOption = 50,
                    minExpiration = 300,
                    maxExpiration = 2592000,
                ),
        )

    val rules =
        this.serverRules
            .orEmpty()
            .associate {
                it to ""
            }.toImmutableMap()

    return UiInstanceMetadata(
        instance =
            UiInstance(
                name = this.name ?: "Unknown",
                description = this.description,
                iconUrl = this.iconURL,
                domain = this.uri ?: "Unknown",
                type = PlatformType.Misskey,
                bannerUrl = this.bannerURL,
                usersCount = 0, // Default to 0 as users count isn't provided in Meta200Response
            ),
        rules = rules,
        configuration = configuration,
    )
}

internal fun Antenna.render(): UiList.Antenna =
    UiList.Antenna(
        id = id,
        title = name,
    )

internal fun Channel.render(accountKey: MicroBlogKey): UiList.Channel =
    UiList.Channel(
        id = id,
        title = name,
        description =
            description
                ?.takeIf {
                    it.isNotEmpty()
                }?.let {
                    misskeyParser.parse(it).toHtml(accountKey, emptyMap(), accountKey.host).toUi()
                },
        isArchived = isArchived ?: false,
        notesCount = notesCount ?: 0.0,
        usersCount = usersCount ?: 0.0,
        banner = bannerUrl,
        isFollowing = isFollowing,
        isFavorited = isFavorited,
    )
