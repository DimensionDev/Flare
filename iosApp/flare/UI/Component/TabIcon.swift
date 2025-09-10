import SwiftUI
import KotlinSharedUI
import Kingfisher
import Awesome

struct TabTitle: View {
    let title: TitleType
    var body: some View {
        switch onEnum(of: title) {
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
            Text(text)
        case .text(let text):
            Text(text.content)
        }
    }
}

struct TabIcon: View {
    let icon: IconType
    let accountType: AccountType
    var body: some View {
        switch onEnum(of: icon) {
        case .material(let material):
            MaterialTabIcon(icon: material.icon)
        case .avatar(let avatar):
            AvatarTabIcon(userKey: avatar.userKey, accountType: accountType)
        case .url(let url):
            KFImage.url(.init(string: url.url))
        case .mixed(let mixed):
            MaterialTabIcon(icon: mixed.icon)
        }
    }
}

struct MaterialTabIcon: View {
    let icon: IconType.MaterialMaterialIcon
    var body: some View {
        switch icon {
        case .home: Awesome.Classic.Solid.house.image
        case .notification: Awesome.Classic.Solid.bell.image
        case .search:Awesome.Classic.Solid.magnifyingGlass.image
        case .profile: Awesome.Classic.Solid.circleUser.image
        case .settings: Awesome.Classic.Solid.gear.image
        case .local: Awesome.Classic.Solid.users.image
        case .world: Awesome.Classic.Solid.globe.image
        case .featured: Awesome.Classic.Solid.rectangleList.image
        case .bookmark: Awesome.Classic.Solid.bookBookmark.image
        case .heart: Awesome.Classic.Solid.star.image
        case .twitter: Awesome.Classic.Brand.twitter.image
        case .mastodon: Awesome.Classic.Brand.mastodon.image
        case .misskey: Awesome.Classic.Solid.globe.image
        case .bluesky: Awesome.Classic.Brand.bluesky.image
        case .list: Awesome.Classic.Solid.list.image
        case .feeds: Awesome.Classic.Solid.squareRss.image
        case .messages: Awesome.Classic.Solid.message.image
        case .rss: Awesome.Classic.Solid.squareRss.image
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
            KFImage.url(.init(string: user.avatar))
        }
    }
}

