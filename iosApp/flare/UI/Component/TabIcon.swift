import SwiftUI
import KotlinSharedUI
import Kingfisher

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
            case .liked: String(localized: "liked_title")
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
                    MaterialTabIcon(icon: mixed.icon)
                        .padding(2)
                        .background(Color.white)
                        .foregroundStyle(Color.black)
                        .clipShape(.circle)
                        .frame(width: size / 2, height: size / 2)
                }
                .frame(width: size, height: size)
            }
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
        switch icon {
        case .home:        Image("fa-house")
                .resizable()
                .scaledToFit()
        case .notification: Image("fa-bell")
                .resizable()
                .scaledToFit()
        case .search:       Image("fa-magnifying-glass")
                .resizable()
                .scaledToFit()
        case .profile:      Image("fa-circle-user")
                .resizable()
                .scaledToFit()
        case .settings:     Image("fa-gear")
                .resizable()
                .scaledToFit()
        case .local:        Image("fa-users")
                .resizable()
                .scaledToFit()
        case .world:        Image("fa-globe")
                .resizable()
                .scaledToFit()
        case .featured:     Image("fa-rectangle-list")
                .resizable()
                .scaledToFit()
        case .bookmark:     Image("fa-book-bookmark")
                .resizable()
                .scaledToFit()
        case .heart:        Image("fa-heart")
                .resizable()
                .scaledToFit()
        case .twitter:      Image("fa-twitter")
                .resizable()
                .scaledToFit()
        case .mastodon:     Image("fa-mastodon")
                .resizable()
                .scaledToFit()
        case .misskey:      Image("fa-globe")
                .resizable()
                .scaledToFit()
        case .bluesky:      Image("fa-bluesky")
                .resizable()
                .scaledToFit()
        case .list:         Image("fa-list")
                .resizable()
                .scaledToFit()
        case .feeds:        Image("fa-square-rss")
                .resizable()
                .scaledToFit()
        case .messages:     Image("fa-message")
                .resizable()
                .scaledToFit()
        case .rss:          Image("fa-square-rss")
                .resizable()
                .scaledToFit()
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
        }
    }
}
