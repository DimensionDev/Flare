import KotlinSharedUI
import SwiftUI
import FlareAppleCore
import FlareAppleUI

enum Route: Hashable, Identifiable {
    case empty
    case notification
    case accountNotification(MicroBlogKey)
    case discover
    case serviceSelect
    case localHistory
    case timeline(UiTimelineTabItem)
    case composeNew
    case composeDraft(String)
    case composeQuote(AccountType, MicroBlogKey)
    case composeReply(AccountType, MicroBlogKey)
    case composeVVOReplyComment(AccountType, MicroBlogKey, String)
    case statusDetail(AccountType, MicroBlogKey)
    case statusVVOComment(AccountType, MicroBlogKey)
    case statusVVOStatus(AccountType, MicroBlogKey)
    case profileUser(AccountType, MicroBlogKey)
    case profileUserNameWithHost(AccountType, String, String)
    case userFollowing(AccountType, MicroBlogKey)
    case userFans(AccountType, MicroBlogKey)
    case mediaImage(String, String?, [String: String]?)
    case mediaRaw([any UiMedia], Int, String?)
    case mediaStatusMedia(AccountType, MicroBlogKey, Int32, String?)
    case deepLinkAccountPicker(String, [MicroBlogKey: Route])
    case rssDetail(String, String?, String?)
    case externalLink(String)

    var id: Int {
        return self.hashValue
    }
    
    static func == (lhs: Route, rhs: Route) -> Bool {
        switch (lhs, rhs) {
        case (.timeline(let lhs), .timeline(let rhs)):
            return lhs.id == rhs.id
        case (.mediaRaw(let lhsMedias, let lhsIndex, let lhsPreview), .mediaRaw(let rhsMedias, let rhsIndex, let rhsPreview)):
            return lhsIndex == rhsIndex &&
                lhsPreview == rhsPreview &&
                lhsMedias.map { $0.url } == rhsMedias.map { $0.url }
        default:
            return lhs.hashValue == rhs.hashValue
        }
    }

    func hash(into hasher: inout Hasher) {
        switch self {
        case .accountNotification(let accountKey):
            hasher.combine("accountNotification")
            hasher.combine(accountKey.host)
            hasher.combine(accountKey.id)
        case .timeline(let item):
            hasher.combine("timeline")
            hasher.combine(item.id)
        case .mediaRaw(let medias, let selectedIndex, let preview):
            hasher.combine("mediaRaw")
            hasher.combine(medias.map { $0.url })
            hasher.combine(selectedIndex)
            hasher.combine(preview)
        default:
            hasher.combine(String(describing: self))
        }
    }

    @MainActor
    @ViewBuilder
    func view(
        onNavigate: @escaping (Route) -> Void,
        goBack: @escaping () -> Void
    ) -> some View {
        switch self {
        case .empty:
            EmptyView()
        case .notification:
            PlaceholderPanel(destination: .notifications)
        case .accountNotification(let accountKey):
            NotificationScreen(accountKey: accountKey)
        case .discover:
            DiscoverScreen()
        case .serviceSelect:
            ServiceSelectionScreen(toHome: goBack)
        case .localHistory:
            LocalHistoryContentScreen()
        case .timeline(let item):
            TimelineScreen(tabItem: item, allowGalleryMode: true)
                .navigationTitle(item.title.text)
        case .composeNew,
                .composeDraft,
                .composeQuote,
                .composeReply,
                .composeVVOReplyComment,
                .mediaImage,
                .mediaRaw,
                .mediaStatusMedia:
            EmptyView()
        case .statusDetail(let accountType, let statusKey):
            StatusDetailScreen(accountType: accountType, statusKey: statusKey)
        case .statusVVOComment(let accountType, let statusKey):
            VVOCommentScreen(accountType: accountType, statusKey: statusKey)
        case .statusVVOStatus(let accountType, let statusKey):
            VVOStatusScreen(accountType: accountType, statusKey: statusKey)
        case .profileUser(let accountType, let userKey):
            ProfileScreen(
                accountType: accountType,
                userKey: userKey,
                onFollowingClick: { key in onNavigate(.userFollowing(accountType, key)) },
                onFansClick: { key in onNavigate(.userFans(accountType, key)) },
                goBack: goBack
            )
        case .profileUserNameWithHost(let accountType, let userName, let host):
            ProfileWithUserNameAndHostScreen(
                userName: userName,
                host: host,
                accountType: accountType,
                onFollowingClick: { key in onNavigate(.userFollowing(accountType, key)) },
                onFansClick: { key in onNavigate(.userFans(accountType, key)) },
                goBack: goBack
            )
        case .userFollowing(let accountType, let userKey):
            UserListScreen(accountType: accountType, userKey: userKey, isFollowing: true)
        case .userFans(let accountType, let userKey):
            UserListScreen(accountType: accountType, userKey: userKey, isFollowing: false)
        case .deepLinkAccountPicker(let originalUrl, let data):
            DeepLinkAccountPickerView(
                originalUrl: originalUrl,
                data: data,
                onNavigate: onNavigate
            )
        case .rssDetail(let url, let descriptionHtml, let title):
            RssDetailScreen(url: url, descriptionHtml: descriptionHtml, descriptionTitle: title)
        case .externalLink:
            EmptyView()
        }
    }
}

extension Route {
    static func fromDeepLinkRoute(deeplinkRoute: DeeplinkRoute) -> Route? {
        switch onEnum(of: deeplinkRoute) {
        case .login:
            .serviceSelect
        case .timeline(let data):
            fromTimeline(data)
        case .status(let status):
            fromStatus(status)
        case .compose(let compose):
            fromCompose(compose)
        case .media(let media):
            fromMedia(media)
        case .profile(let profile):
            fromProfile(profile)
        case .rss(let rss):
            fromRss(rss)
        case .deepLinkAccountPicker(let picker):
            fromAccountPicker(picker)
        case .openLinkDirectly(let data):
            .externalLink(data.url)
        default:
                .empty
        }
    }

    private static func fromCompose(_ compose: DeeplinkRoute.Compose) -> Route? {
        switch onEnum(of: compose) {
        case .new:
            .composeNew
        case .quote(let data):
            .composeQuote(AccountType.Specific(accountKey: data.accountKey), data.statusKey)
        case .reply(let data):
            .composeReply(AccountType.Specific(accountKey: data.accountKey), data.statusKey)
        case .vVOReplyComment(let data):
            .composeVVOReplyComment(
                AccountType.Specific(accountKey: data.accountKey),
                data.replyTo,
                data.rootId
            )
        }
    }

    private static func fromMedia(_ media: DeeplinkRoute.Media) -> Route? {
        switch onEnum(of: media) {
        case .image(let data):
            .mediaImage(data.uri, data.previewUrl, data.customHeaders)
        case .statusMedia(let data):
            .mediaStatusMedia(data.accountType, data.statusKey, Int32(data.index), data.preview)
        case .podcast:
            .empty
        }
    }

    private static func fromTimeline(_ timeline: DeeplinkRoute.Timeline) -> Route? {
        switch onEnum(of: timeline) {
        case .xQTDeviceFollow(let data):
            if let tabItem = XQTUiTimelineTabItemHelpers.shared.xqtDeviceFollow(accountType: data.accountType) {
                .timeline(tabItem)
            } else {
                nil
            }
        }
    }

    private static func fromProfile(_ profile: DeeplinkRoute.Profile) -> Route? {
        switch onEnum(of: profile) {
        case .user(let data):
            .profileUser(data.accountType, data.userKey)
        case .userNameWithHost(let data):
            .profileUserNameWithHost(data.accountType, data.userName, data.host)
        }
    }

    private static func fromStatus(_ status: DeeplinkRoute.Status) -> Route? {
        switch onEnum(of: status) {
        case .detail(let data):
            .statusDetail(data.accountType, data.statusKey)
        case .vVOComment(let data):
            .statusVVOComment(data.accountType, data.commentKey)
        case .vVOStatus(let data):
            .statusVVOStatus(data.accountType, data.statusKey)
        default:
            .empty
        }
    }

    private static func fromRss(_ rss: DeeplinkRoute.Rss) -> Route? {
        switch onEnum(of: rss) {
        case .detail(let data):
            .rssDetail(data.url, data.descriptionHtml, data.title)
        }
    }

    private static func fromAccountPicker(_ picker: DeeplinkRoute.DeepLinkAccountPicker) -> Route? {
        let routes = picker.data.mapValues { route in
            fromDeepLinkRoute(deeplinkRoute: route) ?? .empty
        }
        return .deepLinkAccountPicker(picker.originalUrl, routes)
    }
}
