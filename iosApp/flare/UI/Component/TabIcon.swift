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
        case .channel: String(localized: "channel_title")
        case .default: String(localized: "tab_settings_default")
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
        tabItem: TimelineTabItemV2,
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
            AvatarView(data: user.avatar)
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
