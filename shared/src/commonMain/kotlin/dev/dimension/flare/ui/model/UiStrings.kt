package dev.dimension.flare.ui.model

import kotlinx.serialization.Serializable

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
    Following,
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
