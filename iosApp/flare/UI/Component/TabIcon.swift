import SwiftUI
import KotlinSharedUI

struct TabTitle: View {
    let title: TitleType
    var body: some View {
        Text(title.text)
    }
}

extension TitleType {
    var text: String {
        switch onEnum(of: self) {
        case .localized(let localized):
            let text = switch localized.key {
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
            }
            return text
        case .text(let text):
            return text.content
        }
    }
}

struct TabIcon: View {
    let icon: IconType
    let accountType: AccountType
    let size: CGFloat
    let iconOnly: Bool
    var body: some View {
        switch onEnum(of: icon) {
        case .material(let material):
            MaterialTabIcon(icon: material.icon)
                .frame(width: size, height: size)
        case .avatar(let avatar):
            AvatarTabIcon(userKey: avatar.userKey, accountType: accountType)
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
                    AvatarTabIcon(userKey: mixed.userKey, accountType: accountType)
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
        icon: IconType,
        accountType: AccountType,
    ) {
        self.init(icon: icon, accountType: accountType, size: 20)
    }
    init(
        icon: IconType,
        accountType: AccountType,
        size: CGFloat
    ) {
        self.init(icon: icon, accountType: accountType, size: size, iconOnly: false)
    }
    init(
        icon: IconType,
        accountType: AccountType,
        iconOnly: Bool
    ) {
        self.init(icon: icon, accountType: accountType, size: 20, iconOnly: iconOnly)
    }
}

struct MaterialTabIcon: View {
    let icon: IconType.MaterialMaterialIcon
    var body: some View {
        Image(icon.imageName)
            .resizable()
            .scaledToFit()
    }
}

extension IconType.MaterialMaterialIcon {
    var imageName: String {
        switch self {
        case .home:        "fa-house"
        case .notification: "fa-bell"
        case .search:       "fa-magnifying-glass"
        case .profile:      "fa-circle-user"
        case .settings:     "fa-gear"
        case .local:        "fa-users"
        case .world:        "fa-globe"
        case .featured:     "fa-rectangle-list"
        case .bookmark:     "fa-book-bookmark"
        case .heart:        "fa-heart"
        case .twitter:      "fa-x-twitter"
        case .mastodon:     "fa-mastodon"
        case .misskey:      "fa-misskey"
        case .bluesky:      "fa-bluesky"
        case .list:         "fa-list"
        case .feeds:        "fa-square-rss"
        case .messages:     "fa-message"
        case .rss:          "fa-square-rss"
        case .weibo: "fa-weibo"
        }
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
