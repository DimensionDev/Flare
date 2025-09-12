import SwiftUI
import KotlinSharedUI

enum Route: Hashable {
    static func ==(lhs: Route, rhs: Route) -> Bool {
        return lhs.hashValue == rhs.hashValue
    }

    @MainActor
    @ViewBuilder
    func view(
        onNavigate: @escaping (Route) -> Void
    ) -> some View {
        switch self {
        case .home(let accountType): HomeTimelineScreen(accountType: accountType, toServiceSelect: { onNavigate(.serviceSelect) })
        case .timeline(let item): TimelineScreen(tabItem: item)
        case .serviceSelect:
            ServiceSelectionScreen(toHome: {  })
        default: HomeTimelineScreen(accountType: AccountType.Guest(), toServiceSelect: { onNavigate(.serviceSelect) })
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

    static func fromDeepLink(url: String) -> Route? {
        if let deeplinkRoute = DeeplinkRoute.companion.parse(url: url) {
            switch onEnum(of: deeplinkRoute) {
            case .callback(_):
                return nil
            case .compose(let compose):
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
            case .media(let media):
                switch onEnum(of: media) {
                case .image(let data):
                    return Route.mediaImage(data.uri, data.previewUrl)
                case .podcast(let data):
                    return Route.mediaPodcast(data.accountType, data.id)
                case .statusMedia(let data):
                    return Route.mediaStatusMedia(data.accountType, data.statusKey, Int32(data.index), data.preview)
                }
            case .profile(let profile):
                switch onEnum(of: profile) {
                case .user(let data):
                    return Route.profileUser(data.accountType, data.userKey)
                case .userNameWithHost(let data):
                    return Route.profileUserNameWithHost(data.accountType, data.userName, data.host)
                }
            case .rss(let rss):
                switch onEnum(of: rss) {
                case .detail(let data):
                    return Route.rssDetail(data.url)
                }
            case .search(let search):
                return Route.search(search.accountType, search.query)
            case .status(let status):
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
        } else {
            return nil
        }

    }
}
