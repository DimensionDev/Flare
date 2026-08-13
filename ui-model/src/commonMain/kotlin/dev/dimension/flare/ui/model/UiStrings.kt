package dev.dimension.flare.ui.model

import kotlinx.serialization.Serializable

// strings should add after the last one, otherwise it will break the serialization compatibility.
// If you want to remove a strings, please deprecate it and hide it from the tab/group icon picker instead of removing it directly.
@Serializable
public enum class UiStrings {
    Home,
    Notifications,
    Discover,
    Me,
    Settings,
    MastodonLocal,
    MastodonPublic,
    Featured,
    Bookmark,
    Favourite,
    List,
    Feeds,
    DirectMessage,
    Rss,
    Antenna,
    MixedTimeline,
    Social,
    Liked,
    AllRssFeeds,
    Posts,
    Channel,
    Default,
    Login,
    Verify,
    Cancel,
    Next,
    Username,
    Password,
    Otp,
    OAuthLogin,
    PasswordLogin,
    QrConnect,
    CredentialImport,
    ExternalSigner,
    WebCookieLogin,
    NostrLoginAccount,
    PixivRankingWeek,
    PixivRankingMonth,
    PixivRankingDayMale,
    PixivRankingDayFemale,
    PixivRankingWeekOriginal,
    PixivRankingWeekRookie,
    PixivRankingDayManga,
    Illustrations,
    Manga,
    Following,
    PostsWithReplies,
    Media,
    FanboxSupported,
    FanboxRecommendedCreators,
    PixivPrivateFollowing,
    PixivPrivateBookmarks,
    BlueskyFixDelegationScopes,
    PixivPrivateFavourites,
    Highlights,
    ForYou,
    Popular,
}

public fun UiStrings.asText(): UiText = UiText.Localized(this)

@Serializable
public sealed interface UiText {
    @Serializable
    public data class Localized(
        val string: UiStrings,
    ) : UiText

    @Serializable
    public data class Raw(
        val string: String,
    ) : UiText

    /**
     * A reference to text owned by an external package.
     *
     * Catalog values intentionally live outside this model so persisted tabs do not duplicate
     * every translation. Renderers must use [fallbackText] when the package catalog is not
     * available.
     */
    @Serializable
    public data class ExternalRef(
        val namespace: String,
        val key: String,
        val fallback: String,
        val args: Map<String, UiTextArgument> = emptyMap(),
    ) : UiText {
        public fun fallbackText(): String = fallback.interpolate(args)
    }
}

public val UiText.fallbackString: String
    get() =
        when (this) {
            is UiText.Localized -> string.name
            is UiText.Raw -> string
            is UiText.ExternalRef -> fallbackText()
        }

@Serializable
public sealed interface UiTextArgument {
    public val text: String

    @Serializable
    public data class StringValue(
        val value: String,
    ) : UiTextArgument {
        override val text: String = value
    }

    @Serializable
    public data class NumberValue(
        val value: Double,
    ) : UiTextArgument {
        override val text: String = value.toString().removeSuffix(".0")
    }

    @Serializable
    public data class BooleanValue(
        val value: Boolean,
    ) : UiTextArgument {
        override val text: String = value.toString()
    }
}

private val namedArgumentPattern = Regex("\\{([A-Za-z][A-Za-z0-9_.-]{0,63})}")

private fun String.interpolate(args: Map<String, UiTextArgument>): String =
    namedArgumentPattern.replace(this) { match ->
        args[match.groupValues[1]]?.text ?: match.value
    }
