import SwiftUI
import KotlinSharedUI

struct TabTitle: View {
    let title: Any
    var body: some View {
        Text(String(describing: title))
    }
}

struct TimelineTabTitle: View {
    let title: UiText
    var body: some View {
        Text(title.text)
    }
}

extension UiText {
    var text: String {
        switch onEnum(of: self) {
        case .localized(let localized):
            return localized.string.text
        case .raw(let raw):
            return raw.string
        }
    }
}

extension UiStrings {
    var text: String {
        switch self {
        case .home: String(localized: "home_tab_home_title")
        case .notifications: String(localized: "home_tab_notifications_title")
        case .discover: String(localized: "home_tab_discover_title")
        case .me: String(localized: "home_tab_me_title")
        case .settings: String(localized: "settings_title")
        case .mastodonLocal: String(localized: "mastodon_tab_local_title")
        case .mastodonPublic: String(localized: "mastodon_tab_public_title")
        case .featured: String(localized: "home_tab_featured_title")
        case .bookmark: String(localized: "home_tab_bookmarks_title")
        case .favourite: String(localized: "home_tab_favorite_title")
        case .list: String(localized: "home_tab_list_title")
        case .feeds: String(localized: "home_tab_feeds_title")
        case .directMessage: String(localized: "dm_list_title")
        case .rss: String(localized: "rss_title")
        case .social: String(localized: "social_title")
        case .antenna: String(localized: "antenna_title")
        case .mixedTimeline: String(localized: "mixed_timeline_title")
        case .liked: String(localized: "liked_tab_title")
        case .allRssFeeds: String(localized: "all_rss_feeds_title")
        case .posts: String(localized: "posts_title")
        case .postsWithReplies: String(localized: "posts_with_replies_title", defaultValue: "Posts & Replies")
        case .media: String(localized: "media_title", defaultValue: "Media")
        case .channel: String(localized: "channel_title")
        case .default: String(localized: "tab_settings_default")
        case .login: String(localized: "login_button")
        case .verify: String(localized: "verify_button", defaultValue: "Verify")
        case .cancel: String(localized: "cancel_button")
        case .next: String(localized: "service_select_next_button")
        case .username: String(localized: "bluesky_login_username_hint")
        case .password: String(localized: "bluesky_login_password_hint")
        case .otp: String(localized: "bluesky_login_auth_factor_token_hint")
        case .oauthLogin: String(localized: "bluesky_login_oauth_button")
        case .passwordLogin: String(localized: "bluesky_login_use_password_button")
        case .qrConnect: String(localized: "nostr_login_qr_button")
        case .credentialImport: String(localized: "nostr_login_title")
        case .externalSigner: String(localized: "nostr_login_amber_button")
        case .webCookieLogin: String(localized: "login_button")
        case .nostrLoginAccount: String(localized: "nostr_login_account_hint")
        case .following: String(localized: "misskey_channel_tab_following")
        case .pixivRankingWeek: String(localized: "pixiv_ranking_week_title", defaultValue: "Weekly Ranking")
        case .pixivRankingMonth: String(localized: "pixiv_ranking_month_title", defaultValue: "Monthly Ranking")
        case .pixivRankingDayMale: String(localized: "pixiv_ranking_day_male_title", defaultValue: "Male Ranking")
        case .pixivRankingDayFemale: String(localized: "pixiv_ranking_day_female_title", defaultValue: "Female Ranking")
        case .pixivRankingWeekOriginal: String(localized: "pixiv_ranking_week_original_title", defaultValue: "Original Ranking")
        case .pixivRankingWeekRookie: String(localized: "pixiv_ranking_week_rookie_title", defaultValue: "Rookie Ranking")
        case .pixivRankingDayManga: String(localized: "pixiv_ranking_day_manga_title", defaultValue: "Manga Ranking")
        case .illustrations: String(localized: "illustrations_title", defaultValue: "Illustrations")
        case .manga: String(localized: "manga_title", defaultValue: "Manga")
        }
    }
}

struct TabIcon: View {
    let icon: IconType
    let size: CGFloat
    let iconOnly: Bool

    init(
        icon: IconType,
        size: CGFloat = 20,
        iconOnly: Bool = false
    ) {
        self.icon = icon
        self.size = size
        self.iconOnly = iconOnly
    }

    var body: some View {
        switch onEnum(of: icon) {
        case .material(let material):
            MaterialTabIcon(icon: material.icon)
                .frame(width: size, height: size)
        case .avatar(let avatar):
            AvatarTabIcon(userKey: avatar.accountKey, accountType: AccountType.Specific(accountKey: avatar.accountKey))
                .frame(width: size, height: size)
        case .url(let url):
            NetworkImage(data: url.url)
                .frame(width: size, height: size)
        case .mixed(let mixed):
            if iconOnly {
                MaterialTabIcon(icon: mixed.icon)
                    .frame(width: size, height: size)
            } else {
                ZStack(
                    alignment: .bottomTrailing
                ) {
                    AvatarTabIcon(userKey: mixed.accountKey, accountType: AccountType.Specific(accountKey: mixed.accountKey))
                        .frame(width: size, height: size)
                    MaterialTabIcon(icon: mixed.icon)
                        .padding(2)
                        .background(Color.white)
                        .foregroundStyle(Color.black)
                        .clipShape(.circle)
                        .frame(width: size / 2, height: size / 2)
                }
                .frame(width: size, height: size)
            }
        case .favIcon(let favIcon):
            FavTabIcon(host: favIcon.host)
                .frame(width: size, height: size)
        }
    }
}

extension TabIcon {
    init(
        tabItem: UiTimelineTabItem,
        size: CGFloat = 20,
        iconOnly: Bool = false
    ) {
        self.init(icon: tabItem.icon, size: size, iconOnly: iconOnly)
    }
}

struct MaterialTabIcon: View {
    let icon: UiIcon
    var body: some View {
        Image(icon.imageName)
            .resizable()
            .scaledToFit()
    }
}

struct AvatarTabIcon: View {

    @StateObject private var presenter: KotlinPresenter<UserState>

    init(userKey: MicroBlogKey, accountType: AccountType) {
        self._presenter = .init(wrappedValue: .init(presenter: UserPresenter(accountType: accountType, userKey: userKey)))
    }

    var body: some View {
        StateView(state: presenter.state.user) { user in
            AvatarView(data: user.avatar?.url, customHeader: user.avatar?.customHeaders)
        } loadingContent: {
            Image("fa-globe")
                .resizable()
                .scaledToFit()
                .redacted(reason: .placeholder)
        }
    }
}

struct FavTabIcon: View {
    @StateObject private var presenter: KotlinPresenter<UiState<NSString>>
    
    init(host: String) {
        self._presenter = .init(wrappedValue: .init(presenter: FavIconPresenter(host: host)))
    }
    
    var body: some View {
        StateView(state: presenter.state) { url in
            NetworkImage(data: .init(url))
        } loadingContent: {
            Image("fa-globe")
                .resizable()
                .scaledToFit()
                .redacted(reason: .placeholder)
        }
    }
}
