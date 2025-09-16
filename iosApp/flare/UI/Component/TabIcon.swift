import SwiftUI
import KotlinSharedUI
import Kingfisher
import Awesome

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
    var body: some View {
        switch onEnum(of: icon) {
        case .material(let material):
            MaterialTabIcon(icon: material.icon, size: size)
        case .avatar(let avatar):
            AvatarTabIcon(userKey: avatar.userKey, accountType: accountType)
                .frame(width: size, height: size)
        case .url(let url):
            NetworkImage(data: url.url)
                .frame(width: size, height: size)
        case .mixed(let mixed):
            MaterialTabIcon(icon: mixed.icon, size: size)
        }
    }
}

extension TabIcon {
    init(
        icon: IconType,
        accountType: AccountType,
    ) {
        self.init(icon: icon, accountType: accountType, size: 24)
    }
}

struct MaterialTabIcon: View {
    let icon: IconType.MaterialMaterialIcon
    let size: CGFloat
    var body: some View {
        switch icon {
        case .home: Awesome.Classic.Solid.house.image
                .size(size)
        case .notification: Awesome.Classic.Solid.bell.image
                .size(size)
        case .search:Awesome.Classic.Solid.magnifyingGlass.image
                .size(size)
        case .profile: Awesome.Classic.Solid.circleUser.image
                .size(size)
        case .settings: Awesome.Classic.Solid.gear.image
                .size(size)
        case .local: Awesome.Classic.Solid.users.image
                .size(size)
        case .world: Awesome.Classic.Solid.globe.image
                .size(size)
        case .featured: Awesome.Classic.Solid.rectangleList.image
                .size(size)
        case .bookmark: Awesome.Classic.Solid.bookBookmark.image
                .size(size)
        case .heart: Awesome.Classic.Solid.star.image
                .size(size)
        case .twitter: Awesome.Classic.Brand.twitter.image
                .size(size)
        case .mastodon: Awesome.Classic.Brand.mastodon.image
                .size(size)
        case .misskey: Awesome.Classic.Solid.globe.image
                .size(size)
        case .bluesky: Awesome.Classic.Brand.bluesky.image
                .size(size)
        case .list: Awesome.Classic.Solid.list.image
                .size(size)
        case .feeds: Awesome.Classic.Solid.squareRss.image
                .size(size)
        case .messages: Awesome.Classic.Solid.message.image
                .size(size)
        case .rss: Awesome.Classic.Solid.squareRss.image
                .size(size)
        }
    }
}

struct AvatarTabIcon: View {
    
    @State private var presenter: KotlinPresenter<UserState>
    
    init(userKey: MicroBlogKey, accountType: AccountType) {
        self._presenter = .init(wrappedValue: .init(presenter: UserPresenter(accountType: accountType, userKey: userKey)))
    }
    
    var body: some View {
        StateView(state: presenter.state.user) { user in
            NetworkImage(data: user.avatar)
        }
    }
}
