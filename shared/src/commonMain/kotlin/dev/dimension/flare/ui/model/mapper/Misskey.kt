package dev.dimension.flare.ui.model.mapper

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.datasource.microblog.StatusAction
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.network.misskey.api.model.Antenna
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
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiPoll
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
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

internal fun Notification.render(
    accountKey: MicroBlogKey,
    event: StatusEvent.Misskey,
    references: Map<ReferenceType, StatusContent>,
): UiTimeline {
    val user = user?.render(accountKey)
    val notificationType =
        runCatching {
            NotificationType.entries.first { it.value == type }
        }.getOrNull()
    val topMessageType =
        when (notificationType) {
            NotificationType.Follow ->
                UiTimeline.TopMessage.MessageType.Misskey
                    .Follow(id = id)
            NotificationType.Mention ->
                UiTimeline.TopMessage.MessageType.Misskey
                    .Mention(id = id)
            NotificationType.Reply ->
                UiTimeline.TopMessage.MessageType.Misskey
                    .Reply(id = id)
            NotificationType.Renote ->
                UiTimeline.TopMessage.MessageType.Misskey
                    .Renote(id = id)
            NotificationType.Quote ->
                UiTimeline.TopMessage.MessageType.Misskey
                    .Quote(id = id)
            NotificationType.Reaction ->
                UiTimeline.TopMessage.MessageType.Misskey
                    .Reaction(id = id)
            NotificationType.PollEnded ->
                UiTimeline.TopMessage.MessageType.Misskey
                    .PollEnded(id = id)
            NotificationType.ReceiveFollowRequest ->
                UiTimeline.TopMessage.MessageType.Misskey
                    .ReceiveFollowRequest(id = id)
            NotificationType.FollowRequestAccepted ->
                UiTimeline.TopMessage.MessageType.Misskey
                    .FollowRequestAccepted(id = id)
            NotificationType.AchievementEarned ->
                UiTimeline.TopMessage.MessageType.Misskey.AchievementEarned(
                    id = id,
                    achievement =
                        achievement?.let {
                            MisskeyAchievement.fromString(it)
                        },
                )
            NotificationType.App ->
                UiTimeline.TopMessage.MessageType.Misskey
                    .App(id = id)
            else ->
                UiTimeline.TopMessage.MessageType.Misskey
                    .UnKnown(id = id, type = type)
        }
    val icon =
        when (notificationType) {
            NotificationType.Follow -> UiTimeline.TopMessage.Icon.Follow
            NotificationType.Mention -> UiTimeline.TopMessage.Icon.Mention
            NotificationType.Reply -> UiTimeline.TopMessage.Icon.Reply
            NotificationType.Renote -> UiTimeline.TopMessage.Icon.Retweet
            NotificationType.Quote -> UiTimeline.TopMessage.Icon.Quote
            NotificationType.Reaction -> UiTimeline.TopMessage.Icon.Favourite
            NotificationType.PollEnded -> UiTimeline.TopMessage.Icon.Poll
            NotificationType.ReceiveFollowRequest -> UiTimeline.TopMessage.Icon.Follow
            NotificationType.FollowRequestAccepted -> UiTimeline.TopMessage.Icon.Follow
            NotificationType.AchievementEarned -> UiTimeline.TopMessage.Icon.Info
            NotificationType.App -> UiTimeline.TopMessage.Icon.Info
            else -> UiTimeline.TopMessage.Icon.Info
        }
    val topMessage =
        UiTimeline.TopMessage(
            user = user,
            icon = icon,
            type = topMessageType,
            onClicked = {
                if (user != null) {
                    launcher.launch(AppDeepLink.Profile(accountKey = accountKey, userKey = user.key))
                }
            },
            statusKey = MicroBlogKey(id, accountKey.host),
        )
    val status =
        (references[ReferenceType.Notification] as? StatusContent.Misskey)
            ?.data
            ?.let {
                if (notificationType == NotificationType.Renote) {
                    it.renote
                } else {
                    it
                }
            }?.renderStatus(accountKey, event)
    return UiTimeline(
        topMessage = topMessage,
        content =
            when {
                notificationType in
                    listOf(
                        NotificationType.Follow,
                        NotificationType.FollowRequestAccepted,
                    ) &&
                    user != null
                ->
                    UiTimeline.ItemContent.User(user)
                notificationType == NotificationType.ReceiveFollowRequest && user != null ->
                    UiTimeline.ItemContent.User(
                        user,
                        button =
                            persistentListOf(
                                UiTimeline.ItemContent.User.Button.AcceptFollowRequest(
                                    onClicked = {
                                        event.acceptFollowRequest(
                                            userKey = user.key,
                                            notificationStatusKey = MicroBlogKey(id, accountKey.host),
                                        )
                                    },
                                ),
                                UiTimeline.ItemContent.User.Button.RejectFollowRequest(
                                    onClicked = {
                                        event.rejectFollowRequest(
                                            userKey = user.key,
                                            notificationStatusKey = MicroBlogKey(id, accountKey.host),
                                        )
                                    },
                                ),
                            ),
                    )

                else ->
                    status ?: user?.let { UiTimeline.ItemContent.User(it) }
            },
    )
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

internal fun Note.render(
    accountKey: MicroBlogKey,
    event: StatusEvent.Misskey,
    references: Map<ReferenceType, StatusContent>,
): UiTimeline {
    val user = user.render(accountKey)
    val renote = (references[ReferenceType.Retweet] as? StatusContent.Misskey)?.data
    val currentStatus = this.renderStatus(accountKey, event)
    val actualStatus = renote ?: this
    val topMessage =
        // TODO: find a way to detect pinned status
        if (false) {
            UiTimeline.TopMessage(
                user = user,
                icon = UiTimeline.TopMessage.Icon.Pin,
                type =
                    UiTimeline.TopMessage.MessageType.Misskey
                        .Pinned(id = id),
                onClicked = {},
                statusKey = currentStatus.statusKey,
            )
        } else if (renote == null || !text.isNullOrEmpty()) {
            null
        } else {
            UiTimeline.TopMessage(
                user = user,
                icon = UiTimeline.TopMessage.Icon.Retweet,
                type =
                    UiTimeline.TopMessage.MessageType.Misskey
                        .Renote(id = id),
                onClicked = {
                    launcher.launch(
                        AppDeepLink.Profile(
                            accountKey = accountKey,
                            userKey = user.key,
                        ),
                    )
                },
                statusKey = currentStatus.statusKey,
            )
        }
    return UiTimeline(
        topMessage = topMessage,
        content = actualStatus.renderStatus(accountKey, event),
    )
}

private fun Note.renderStatus(
    accountKey: MicroBlogKey,
    event: StatusEvent.Misskey,
): UiTimeline.ItemContent.Status {
    val remoteHost =
        if (user.host.isNullOrEmpty()) {
            accountKey.host
        } else {
            user.host
        }
    val user = user.render(accountKey)
    val isFromMe = user.key == accountKey
    val canReblog = visibility in listOf(Visibility.Public, Visibility.Home) || (isFromMe && visibility != Visibility.Specified)
    val renderedVisibility =
        when (visibility) {
            Visibility.Public -> UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Public
            Visibility.Home -> UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Home
            Visibility.Followers -> UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Followers
            Visibility.Specified -> UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Specified
        }
    val statusKey =
        MicroBlogKey(
            id,
            host = user.key.host,
        )
    val reaction =
        reactions
            .map { emoji ->
                UiTimeline.ItemContent.Status.BottomContent.Reaction.EmojiReaction(
                    name = emoji.key,
                    count = emoji.value,
                    url = resolveMisskeyEmoji(emoji.key, accountKey.host, emojis),
                    isUnicode = !emoji.key.startsWith(':') && !emoji.key.endsWith(':'),
                    onClicked = {
                        event.react(
                            statusKey = statusKey,
                            hasReacted = myReaction == emoji.key,
                            reaction = emoji.key,
                        )
                    },
                    me = myReaction == emoji.key,
                )
            }.sortedByDescending { it.count }
            .toPersistentList()
    return UiTimeline.ItemContent.Status(
        images =
            files
                ?.mapNotNull { file ->
                    file.toUi()
                }?.toPersistentList() ?: persistentListOf(),
        contentWarning =
            cw?.let {
                misskeyParser.parse(it).toHtml(accountKey, emojis, remoteHost).toUi()
            },
        user = user,
        quote =
            listOfNotNull(
                if (text != null || !files.isNullOrEmpty() || cw != null || poll != null) {
                    renote?.renderStatus(accountKey, event)
                } else {
                    null
                },
            ).toImmutableList(),
        content = misskeyParser.parse(text.orEmpty()).toHtml(accountKey, emojis, remoteHost).toUi(),
        actions =
            listOfNotNull(
                StatusAction.Item.Reply(
                    count = repliesCount.toLong(),
                    onClicked = {
                        launcher.launch(
                            AppDeepLink.Compose.Reply(
                                accountKey = accountKey,
                                statusKey = statusKey,
                            ),
                        )
                    },
                ),
                if (canReblog) {
                    StatusAction.Group(
                        displayItem =
                            StatusAction.Item.Retweet(
                                count = renoteCount.toLong(),
                                retweeted = false,
                                onClicked = {},
                            ),
                        actions =
                            listOfNotNull(
                                StatusAction.Item.Retweet(
                                    count = renoteCount.toLong(),
                                    retweeted = false,
                                    onClicked = {
                                        event.renote(
                                            statusKey = statusKey,
                                        )
                                    },
                                ),
                                StatusAction.Item.Quote(
                                    count = 0,
                                    onClicked = {
                                        launcher.launch(
                                            AppDeepLink.Compose.Quote(
                                                accountKey = accountKey,
                                                statusKey = statusKey,
                                            ),
                                        )
                                    },
                                ),
                            ).toImmutableList(),
                    )
                } else {
                    null
                },
                StatusAction.Item.Reaction(
                    reacted = myReaction != null,
                    onClicked = {
                        if (myReaction == null) {
                            launcher.launch(
                                AppDeepLink.AddReaction(
                                    accountKey = accountKey,
                                    statusKey = statusKey,
                                ),
                            )
                        } else {
                            event.react(
                                statusKey = statusKey,
                                hasReacted = true,
                                reaction = myReaction,
                            )
                        }
                    },
                ),
                StatusAction.Group(
                    displayItem = StatusAction.Item.More,
                    actions =
                        listOfNotNull(
                            StatusAction.AsyncActionItem(
                                flow =
                                    event
                                        .favouriteState(
                                            statusKey = statusKey,
                                        ).map {
                                            StatusAction.Item.Like(
                                                count = 0,
                                                liked = it,
                                                onClicked = {
                                                    event.favourite(
                                                        statusKey = statusKey,
                                                        favourited = it,
                                                    )
                                                },
                                            )
                                        },
                            ),
                            if (isFromMe) {
                                StatusAction.Item.Delete(
                                    onClicked = {
                                        launcher.launch(
                                            AppDeepLink.DeleteStatus(
                                                accountKey = accountKey,
                                                statusKey = statusKey,
                                            ),
                                        )
                                    },
                                )
                            } else {
                                StatusAction.Item.Report(
                                    onClicked = {
                                        launcher.launch(
                                            AppDeepLink.Misskey.ReportStatus(
                                                accountKey = accountKey,
                                                statusKey = statusKey,
                                                userKey = user.key,
                                            ),
                                        )
                                    },
                                )
                            },
                        ).toImmutableList(),
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
                    onVote = { options ->
                        event.vote(
                            statusKey = statusKey,
                            options = options,
                        )
                    },
                    enabled = !isFromMe,
                )
            },
        statusKey = statusKey,
        card = null,
        createdAt = Instant.parse(createdAt).toUi(),
        topEndContent =
            UiTimeline.ItemContent.Status.TopEndContent
                .Visibility(renderedVisibility),
        bottomContent =
            UiTimeline.ItemContent.Status.BottomContent
                .Reaction(reaction),
        sensitive = files?.any { it.isSensitive } ?: false,
        onClicked = {
            launcher.launch(
                AppDeepLink.StatusDetail(
                    accountKey = accountKey,
                    statusKey = statusKey,
                ),
            )
        },
        platformType = PlatformType.Misskey,
        onMediaClicked = { media, index ->
            launcher.launch(
                AppDeepLink.StatusMedia(
                    accountKey = accountKey,
                    statusKey = statusKey,
                    mediaIndex = index,
                    preview =
                        when (media) {
                            is UiMedia.Image -> media.previewUrl
                            is UiMedia.Video -> media.thumbnailUrl
                            is UiMedia.Audio -> null
                            is UiMedia.Gif -> media.previewUrl
                        },
                ),
            )
        },
        url =
            buildString {
                if (!uri.isNullOrEmpty()) {
                    append(uri)
                } else if (!url.isNullOrEmpty()) {
                    append(url)
                } else {
                    append("https://")
                    append(accountKey.host)
                    append("/notes/")
                    append(id)
                }
            },
    )
}

private fun DriveFile.toUi(): UiMedia? {
    if (type.startsWith("image/")) {
        return UiMedia.Image(
            url = url.orEmpty(),
            previewUrl = thumbnailUrl.orEmpty(),
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
        name = parseName(name.orEmpty(), accountKey, emojis).toUi(),
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
        onClicked = {
            launcher.launch(AppDeepLink.Profile(accountKey = accountKey, userKey = userKey))
        },
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
        name = parseName(name.orEmpty(), accountKey, emojis).toUi(),
        handle = "@$username@$remoteHost",
        key = userKey,
        banner = bannerUrl,
        description = description?.let { misskeyParser.parse(it).toHtml(accountKey, emojis, remoteHost).toUi() },
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
                                    key to misskeyParser.parse(value).toHtml(accountKey, emojis, remoteHost).toUi()
                                }.toImmutableMap(),
                    )
                },
        platformType = PlatformType.Misskey,
        onClicked = {
            launcher.launch(AppDeepLink.Profile(accountKey = accountKey, userKey = userKey))
        },
    )
}

internal fun EmojiSimple.toUi(): UiEmoji =
    UiEmoji(
        shortcode = ":$name:",
        url = url,
        category = category.orEmpty(),
        searchKeywords = aliases + name,
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
    return misskeyNameParser.parse(name).toHtml(accountKey, emojis, accountKey.host) as? Element ?: Element("body")
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
            Element("fn").apply {
//                attributes["name"] = name
                attributes().put("name", name)
                content.forEach {
                    appendChild(it.toHtml(accountKey, emojis, remoteHost))
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
                attributes().put("href", AppDeepLink.Search(accountKey, "$$content"))
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
                attributes().put("href", AppDeepLink.Search(accountKey, "#$tag"))
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
                val deeplink = AppDeepLink.ProfileWithNameAndHost(accountKey, userName, host ?: remoteHost)
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

internal fun UserList.render(): UiList =
    UiList(
        id = id,
        title = name,
        platformType = PlatformType.Misskey,
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

internal fun Antenna.render(): UiList =
    UiList(
        id = id,
        title = name,
        platformType = PlatformType.Misskey,
        type = UiList.Type.Antenna,
    )
