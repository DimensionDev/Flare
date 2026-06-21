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
}
