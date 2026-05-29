package dev.dimension.flare.data.model.tab

import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public object TimelineSpecIds {
    public const val COMMON_HOME: String = "common.home"
    public const val COMMON_DISCOVER: String = "common.discover"
    public const val COMMON_LIST: String = "common.list"

    public const val RSS_FEED: String = "rss.feed"
    public const val RSS_ALL: String = "rss.all"
    public const val RSS_SUBSCRIPTION: String = "rss.subscription"

    public const val MASTODON_LOCAL: String = "mastodon.local"
    public const val MASTODON_PUBLIC: String = "mastodon.public"
    public const val MASTODON_BOOKMARK: String = "mastodon.bookmark"
    public const val MASTODON_FAVOURITE: String = "mastodon.favourite"

    public const val MISSKEY_FAVOURITE: String = "misskey.favourite"
    public const val MISSKEY_HYBRID: String = "misskey.hybrid"
    public const val MISSKEY_LOCAL: String = "misskey.local"
    public const val MISSKEY_GLOBAL: String = "misskey.global"
    public const val MISSKEY_ANTENNA: String = "misskey.antenna"
    public const val MISSKEY_CHANNEL: String = "misskey.channel"

    public const val BLUESKY_BOOKMARK: String = "bluesky.bookmark"
    public const val BLUESKY_FEED: String = "bluesky.feed"

    public const val XQT_FEATURED: String = "xqt.featured"
    public const val XQT_BOOKMARK: String = "xqt.bookmark"
    public const val XQT_DEVICE_FOLLOW: String = "xqt.device_follow"

    public const val VVO_FAVORITE: String = "vvo.favorite"
    public const val VVO_LIKED: String = "vvo.liked"

    public val legacyMigrationIds: Set<String> =
        setOf(
            COMMON_HOME,
            COMMON_DISCOVER,
            COMMON_LIST,
            RSS_FEED,
            RSS_ALL,
            RSS_SUBSCRIPTION,
            MASTODON_LOCAL,
            MASTODON_PUBLIC,
            MASTODON_BOOKMARK,
            MASTODON_FAVOURITE,
            MISSKEY_FAVOURITE,
            MISSKEY_HYBRID,
            MISSKEY_LOCAL,
            MISSKEY_GLOBAL,
            MISSKEY_ANTENNA,
            MISSKEY_CHANNEL,
            BLUESKY_BOOKMARK,
            BLUESKY_FEED,
            XQT_FEATURED,
            XQT_BOOKMARK,
            XQT_DEVICE_FOLLOW,
            VVO_FAVORITE,
            VVO_LIKED,
        )
}
