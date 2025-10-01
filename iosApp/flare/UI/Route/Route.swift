import SwiftUI
import KotlinSharedUI

enum Route: Hashable, Identifiable {
    var id: Int {
        return self.hashValue
    }
    
    static func == (lhs: Route, rhs: Route) -> Bool {
        return lhs.hashValue == rhs.hashValue
    }

    @MainActor
    @ViewBuilder
    func view(
        onNavigate: @escaping (Route) -> Void,
        clearToHome: @escaping () -> Void
    ) -> some View {
        switch self {
        case .home(let accountType): HomeTimelineScreen(accountType: accountType, toServiceSelect: { onNavigate(.serviceSelect) }, toCompose: { onNavigate(.composeNew(accountType)) }, toTabSetting: { onNavigate(.tabSettings) })
        case .timeline(let item): TimelineScreen(tabItem: item)
        case .serviceSelect:
            ServiceSelectionScreen(toHome: { clearToHome() })
        case .statusDetail(let accountType, let statusKey):
            StatusDetailScreen(accountType: accountType, statusKey: statusKey)
        case .profileUser(let accountType, let userKey):
            ProfileScreen(accountType: accountType, userKey: userKey)
        case .settings:
            SettingsScreen()
        case .tabItem(let tabItem):
            tabItem.view(onNavigate: onNavigate)
        case .accountManagement:
            AccountManagementScreen()
        case .search(let accountType, let query):
            SearchScreen(accountType: accountType, initialQuery: query)
        case .composeNew(let accountType):
            ComposeScreen(accountType: accountType)
        case .composeQuote(let accountType, let statusKey):
            ComposeScreen(accountType: accountType, composeStatus: ComposeStatus.Quote(statusKey: statusKey))
        case .composeReply(let accountType, let statusKey):
            ComposeScreen(accountType: accountType, composeStatus: ComposeStatus.Reply(statusKey: statusKey))
        case .composeVVOReplyComment(let accountType, let statueKey, let rootId):
            ComposeScreen(accountType: accountType, composeStatus: ComposeStatus.VVOComment(statusKey: statueKey, rootId: rootId))
        case .profileUserNameWithHost(let accountType, let userName, let host):
            ProfileWithUserNameAndHostScreen(userName: userName, host: host, accountType: accountType)
        case .appearance:
            AppearanceScreen()
        case .about:
            AboutScreen()
        case .localHostory:
            LocalHistoryScreen()
        case .storage:
            StorageScreen()
        case .localFilter:
            LocalFilterScreen()
        case .aiConfig:
            AiConfigScreen()
        case .tabSettings:
            TabSettingsScreen()
        case .rssDetail(let url):
            RssDetailScreen(url: url)
        default:
            Text("Not done yet for \(self)")
        }
    }

    case home(AccountType)
    case timeline(TimelineTabItem)
    case composeNew(AccountType)
    case composeQuote(AccountType, MicroBlogKey)
    case composeReply(AccountType, MicroBlogKey)
    case composeVVOReplyComment(AccountType, MicroBlogKey, String)
    case mediaImage(String, String?)
    case mediaPodcast(AccountType, String)
    case mediaStatusMedia(AccountType, MicroBlogKey, Int32, String?)
    case profileUser(AccountType, MicroBlogKey)
    case profileUserNameWithHost(AccountType, String, String)
    case rssDetail(String)
    case search(AccountType, String)
    case statusAddReaction(AccountType, MicroBlogKey)
    case statusAltText(String)
    case statusBlueskyReport(AccountType, MicroBlogKey)
    case statusDeleteConfirm(AccountType, MicroBlogKey)
    case statusDetail(AccountType, MicroBlogKey)
    case statusMastodonReport(AccountType, MicroBlogKey, MicroBlogKey?)
    case statusMisskeyReport(AccountType, MicroBlogKey, MicroBlogKey?)
    case statusVVOComment(AccountType, MicroBlogKey)
    case statusVVOStatus(AccountType, MicroBlogKey)
    case serviceSelect
    case accountManagement
    case localFilter
    case localHostory
    case moreMenuCustomize
    case aiConfig
    case storage
    case appearance
    case settings
    case about
    case tabItem(TabItem)
    case tabSettings

    fileprivate static func fromCompose(_ compose: DeeplinkRoute.Compose) -> Route? {
        switch onEnum(of: compose) {
        case .new(let data):
            return Route.composeNew(data.accountType)
        case .quote(let data):
            return Route.composeQuote(AccountType.Specific(accountKey: data.accountKey), data.statusKey)
        case .reply(let data):
            return Route.composeReply(AccountType.Specific(accountKey: data.accountKey), data.statusKey)
        case .vVOReplyComment(let data):
            return Route.composeVVOReplyComment(AccountType.Specific(accountKey: data.accountKey), data.replyTo, data.rootId)
        }
    }
    
    fileprivate static func fromMedia(_ media: DeeplinkRoute.Media) -> Route? {
        switch onEnum(of: media) {
        case .image(let data):
            return Route.mediaImage(data.uri, data.previewUrl)
        case .podcast(let data):
            return Route.mediaPodcast(data.accountType, data.id)
        case .statusMedia(let data):
            return Route.mediaStatusMedia(data.accountType, data.statusKey, Int32(data.index), data.preview)
        }
    }
    
    fileprivate static func fromProfile(_ profile: DeeplinkRoute.Profile) -> Route? {
        switch onEnum(of: profile) {
        case .user(let data):
            return Route.profileUser(data.accountType, data.userKey)
        case .userNameWithHost(let data):
            return Route.profileUserNameWithHost(data.accountType, data.userName, data.host)
        }
    }
    
    fileprivate static func fromStatus(_ status: DeeplinkRoute.Status) -> Route? {
        switch onEnum(of: status) {
        case .addReaction(let data):
            return Route.statusAddReaction(data.accountType, data.statusKey)
        case .altText(let data):
            return Route.statusAltText(data.text)
        case .blueskyReport(let data):
            return Route.statusBlueskyReport(data.accountType, data.statusKey)
        case .deleteConfirm(let data):
            return Route.statusDeleteConfirm(data.accountType, data.statusKey)
        case .detail(let data):
            return Route.statusDetail(data.accountType, data.statusKey)
        case .mastodonReport(let data):
            return Route.statusMastodonReport(data.accountType, data.userKey, data.statusKey)
        case .misskeyReport(let data):
            return Route.statusMisskeyReport(data.accountType, data.userKey, data.statusKey)
        case .vVOComment(let data):
            return Route.statusVVOComment(data.accountType, data.commentKey)
        case .vVOStatus(let data):
            return Route.statusVVOStatus(data.accountType, data.statusKey)
        }
    }
    
    static func fromDeepLink(url: String) -> Route? {
        if let deeplinkRoute = DeeplinkRoute.companion.parse(url: url) {
            switch onEnum(of: deeplinkRoute) {
            case .callback:
                return nil
            case .compose(let compose):
                return fromCompose(compose)
            case .media(let media):
                return fromMedia(media)
            case .profile(let profile):
                return fromProfile(profile)
            case .rss(let rss):
                switch onEnum(of: rss) {
                case .detail(let data):
                    return Route.rssDetail(data.url)
                }
            case .search(let search):
                return Route.search(search.accountType, search.query)
            case .status(let status):
                return fromStatus(status)
            case .login:
                return Route.serviceSelect
            }
        } else {
            return nil
        }

    }
}
